import json
import unittest
from unittest.mock import patch

import requests

from ticketiq_api import ApiError, TicketIqApi


def response(status_code: int, body: dict | None = None) -> requests.Response:
    result = requests.Response()
    result.status_code = status_code
    result.headers["Content-Type"] = "application/json"
    result._content = b"" if body is None else json.dumps(body).encode()
    return result


class TicketIqApiTests(unittest.TestCase):
    def test_authenticated_request_sends_bearer_token(self) -> None:
        tokens = {"accessToken": "access-token", "refreshToken": "refresh-token"}
        api = TicketIqApi("https://ticketiq.example", tokens)
        with patch("requests.request", return_value=response(200, {"items": []})) as request:
            self.assertEqual(api.request("GET", "/api/tickets"), {"items": []})
        self.assertEqual(request.call_args.kwargs["headers"]["Authorization"], "Bearer access-token")

    def test_refreshes_expired_access_token_and_retries_once(self) -> None:
        tokens = {"accessToken": "old-access", "refreshToken": "old-refresh"}
        api = TicketIqApi("https://ticketiq.example", tokens)
        with patch(
            "requests.request",
            side_effect=[
                response(401, {"message": "expired"}),
                response(200, {"accessToken": "new-access", "refreshToken": "new-refresh"}),
                response(200, {"items": []}),
            ],
        ) as request:
            self.assertEqual(api.request("GET", "/api/tickets"), {"items": []})
        self.assertEqual(tokens["accessToken"], "new-access")
        self.assertEqual(tokens["refreshToken"], "new-refresh")
        self.assertEqual(request.call_count, 3)
        self.assertEqual(request.call_args.kwargs["headers"]["Authorization"], "Bearer new-access")

    def test_failed_refresh_clears_session_and_reports_expiry(self) -> None:
        tokens = {"accessToken": "expired", "refreshToken": "invalid"}
        api = TicketIqApi("https://ticketiq.example", tokens)
        with patch("requests.request", side_effect=[response(401), response(401)]):
            with self.assertRaisesRegex(ApiError, "session expired") as caught:
                api.request("GET", "/api/tickets")
        self.assertEqual(caught.exception.status_code, 401)
        self.assertEqual(tokens, {})

    def test_validation_errors_include_field_details(self) -> None:
        api = TicketIqApi("https://ticketiq.example")
        with patch(
            "requests.request",
            return_value=response(400, {"message": "Invalid request", "fieldErrors": {"title": "must not be blank"}}),
        ):
            with self.assertRaisesRegex(ApiError, "title: must not be blank"):
                api.request("POST", "/api/tickets", body={"title": ""})

    def test_rejects_non_http_api_url(self) -> None:
        with self.assertRaisesRegex(ValueError, "absolute http"):
            TicketIqApi("file:///tmp/api")


if __name__ == "__main__":
    unittest.main()
