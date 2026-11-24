"""HTTP client for the TicketIQ Spring Boot API."""

from collections.abc import Mapping, MutableMapping
from typing import Any
from urllib.parse import urlsplit

import requests


class ApiError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


class TicketIqApi:
    def __init__(
        self,
        base_url: str,
        tokens: MutableMapping[str, str] | None = None,
        timeout: tuple[float, float] = (5, 20),
    ):
        base_url = base_url.strip().rstrip("/")
        parsed_url = urlsplit(base_url)
        if parsed_url.scheme not in {"http", "https"} or not parsed_url.netloc:
            raise ValueError("TICKETIQ_API_URL must be an absolute http:// or https:// URL")
        if parsed_url.username or parsed_url.password:
            raise ValueError("TICKETIQ_API_URL must not contain credentials")
        self.base_url = base_url
        self.tokens = tokens
        self.timeout = timeout

    def request(
        self,
        method: str,
        path: str,
        *,
        body: Mapping[str, Any] | None = None,
        params: Mapping[str, Any] | None = None,
        authenticated: bool = True,
        retry_auth: bool = True,
    ) -> Any:
        response = self._send(method, path, body=body, params=params, authenticated=authenticated)
        if response.status_code == 401 and authenticated and retry_auth and self._refresh_tokens():
            return self.request(
                method,
                path,
                body=body,
                params=params,
                authenticated=authenticated,
                retry_auth=False,
            )
        if response.status_code == 401 and authenticated:
            if self.tokens is not None:
                self.tokens.clear()
            raise ApiError("Your session expired. Please sign in again.", status_code=401)
        return self._decode_response(response)

    def _send(
        self,
        method: str,
        path: str,
        *,
        body: Mapping[str, Any] | None = None,
        params: Mapping[str, Any] | None = None,
        authenticated: bool,
    ) -> requests.Response:
        headers = {}
        if authenticated and self.tokens and self.tokens.get("accessToken"):
            headers["Authorization"] = f"Bearer {self.tokens['accessToken']}"
        try:
            return requests.request(
                method,
                f"{self.base_url}/{path.lstrip('/')}",
                headers=headers,
                json=body,
                params=params,
                timeout=self.timeout,
            )
        except requests.RequestException as error:
            raise ApiError(f"Could not reach the TicketIQ API: {error}") from error

    def _refresh_tokens(self) -> bool:
        if self.tokens is None or not self.tokens.get("refreshToken"):
            return False
        response = self._send(
            "POST",
            "/api/auth/refresh",
            body={"refreshToken": self.tokens["refreshToken"]},
            authenticated=False,
        )
        if response.status_code == 401:
            return False
        data = self._decode_response(response)
        if not isinstance(data, dict) or not data.get("accessToken") or not data.get("refreshToken"):
            raise ApiError("The TicketIQ API returned an invalid refresh response.")
        self.tokens["accessToken"] = data["accessToken"]
        self.tokens["refreshToken"] = data["refreshToken"]
        return True

    @staticmethod
    def _decode_response(response: requests.Response) -> Any:
        if response.status_code == 204:
            return None
        try:
            data = response.json()
        except ValueError as error:
            if not response.ok:
                raise ApiError(
                    f"TicketIQ API request failed with HTTP {response.status_code}.",
                    status_code=response.status_code,
                ) from error
            raise ApiError("The TicketIQ API returned invalid JSON.", status_code=response.status_code) from error
        if not response.ok:
            message = data.get("message", "Request failed") if isinstance(data, dict) else "Request failed"
            field_errors = data.get("fieldErrors") if isinstance(data, dict) else None
            if isinstance(field_errors, dict):
                message += " " + "; ".join(f"{field}: {detail}" for field, detail in field_errors.items())
            raise ApiError(message, status_code=response.status_code)
        return data
