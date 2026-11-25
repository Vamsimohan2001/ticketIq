"""Streamlit frontend for the TicketIQ Spring Boot application."""

import os
from typing import Any

import streamlit as st
from streamlit.errors import StreamlitSecretNotFoundError

from ticketiq_api import ApiError, TicketIqApi


STATUSES = ["NEW", "TRIAGED", "NEEDS_HUMAN_TRIAGE", "IN_PROGRESS", "RESOLVED", "CLOSED"]
CATEGORIES = ["ACCESS", "BILLING", "BUG", "HOW_TO", "INTEGRATION", "FEATURE_REQUEST", "OTHER"]
PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
AGENT_TEAMS = [
    "Service Desk",
    "HR Systems",
    "Finance Systems",
    "Application Engineering",
    "Identity and Access",
]


def _api_url() -> str:
    configured_url = os.getenv("TICKETIQ_API_URL")
    if configured_url:
        return configured_url
    try:
        return st.secrets["TICKETIQ_API_URL"]
    except (KeyError, StreamlitSecretNotFoundError):
        return "http://localhost:8080"


def _label(value: Any) -> str:
    return str(value or "").replace("_", " ").title()


def _render_auth(api: TicketIqApi) -> None:
    st.subheader("Sign in to your support desk")
    sign_in, request_account = st.tabs(["Sign in", "Request an account"])
    with sign_in:
        with st.form("login_form"):
            user_id = st.text_input("User ID")
            password = st.text_input("Password", type="password")
            role = st.selectbox("Role", ["USER", "AGENT", "ADMIN"])
            submitted = st.form_submit_button("Sign in", type="primary")
        if submitted:
            login = api.request(
                "POST",
                "/api/auth/login",
                body={"userId": user_id.strip(), "password": password, "role": role},
                authenticated=False,
            )
            st.session_state.ticketiq_session = {
                "user": login["user"],
                "tokens": {
                    "accessToken": login["accessToken"],
                    "refreshToken": login["refreshToken"],
                },
            }
            st.session_state.pop("ticket_filters", None)
            st.session_state.pop("selected_ticket_id", None)
            st.session_state.pop("page", None)
            st.rerun()
        st.caption("Demo accounts (password for all: `12345678`)")
        st.markdown(
            "- **USER:** Uma User (`user@ticketiq.local`)\n"
            "- **AGENT:** Asha Agent (`agent@ticketiq.local`), Harper HR "
            "(`agent.hr@ticketiq.local`), Finley Finance (`agent.finance@ticketiq.local`), "
            "Taylor Technical (`agent.engineering@ticketiq.local`), Ari Access "
            "(`agent.access@ticketiq.local`)\n"
            "- **ADMIN:** Alex Admin (`admin@ticketiq.local`)"
        )
    with request_account:
        with st.form("signup_form"):
            role = st.selectbox("Requested role", ["USER", "AGENT", "ADMIN"])
            employee_id = st.text_input("Employee ID (optional)")
            full_name = st.text_input("Full name")
            user_id = st.text_input("User ID")
            password = st.text_input("Password", type="password")
            team = st.selectbox("Support team (for agent accounts)", AGENT_TEAMS)
            submitted = st.form_submit_button("Request account")
        if submitted:
            request_body = {
                "employeeId": employee_id.strip() or None,
                "fullName": full_name.strip(),
                "userId": user_id.strip(),
                "password": password,
                "role": role,
                "team": team if role == "AGENT" else None,
            }
            api.request(
                "POST",
                "/api/auth/signup",
                body=request_body,
                authenticated=False,
            )
            st.success("Account request submitted. An administrator must approve it before you can sign in.")


def _render_ticket_list(api: TicketIqApi, is_staff: bool) -> None:
    st.subheader("Incident queue" if is_staff else "My incidents")
    filters = st.session_state.get("ticket_filters", {})
    with st.form("ticket_filters"):
        first, second, third = st.columns(3)
        query = first.text_input("Search titles", value=filters.get("q", ""))
        status_options = [""] + STATUSES
        category_options = [""] + CATEGORIES
        priority_options = [""] + PRIORITIES
        status = second.selectbox(
            "Status", status_options, index=status_options.index(filters.get("status", ""))
        ) if is_staff else ""
        category = third.selectbox(
            "Category", category_options, index=category_options.index(filters.get("category", ""))
        )
        priority = st.selectbox(
            "Priority", priority_options, index=priority_options.index(filters.get("priority", ""))
        )
        submitted = st.form_submit_button("Apply filters")
    if submitted:
        filters = {"q": query, "status": status, "category": category, "priority": priority}
        st.session_state.ticket_filters = filters

    params = {"page": 0, "size": 50}
    params.update({key: value for key, value in filters.items() if value})
    page = api.request("GET", "/api/tickets", params=params)
    if not page["items"]:
        st.info("No incidents match these filters.")
    for ticket in page["items"]:
        with st.container(border=True):
            left, right = st.columns([5, 1])
            left.text(f"#{ticket['id']} · {ticket['title']}")
            left.text(
                f"{_label(ticket.get('status'))} · {_label(ticket.get('category'))} · "
                f"{_label(ticket.get('priority'))} · {ticket.get('suggestedTeam') or 'Unassigned team'}"
            )
            if is_staff and ticket.get("aiSummary"):
                left.text(f"AI summary: {ticket['aiSummary']}")
            if right.button("Open", key=f"open_ticket_{ticket['id']}"):
                st.session_state.selected_ticket_id = ticket["id"]
                st.session_state.navigation_target = "Ticket details"
                st.rerun()
    st.caption(f"{page['totalItems']} incident(s)")


def _render_new_ticket(api: TicketIqApi) -> None:
    st.subheader("Raise an incident")
    with st.form("new_ticket_form"):
        title = st.text_input("Title", max_chars=200)
        description = st.text_area("What is happening?", max_chars=4000, height=180)
        submitted = st.form_submit_button("Submit ticket", type="primary")
    if submitted:
        ticket = api.request(
            "POST",
            "/api/tickets",
            body={"title": title, "description": description},
        )
        st.session_state.selected_ticket_id = ticket["id"]
        st.session_state.navigation_target = "Ticket details"
        st.success(f"Ticket #{ticket['id']} created.")
        st.rerun()


def _render_ticket_details(api: TicketIqApi, user: dict[str, Any]) -> None:
    ticket_id = st.session_state.get("selected_ticket_id")
    if not ticket_id:
        st.info("Choose an incident from the queue first.")
        return
    ticket = api.request("GET", f"/api/tickets/{ticket_id}")
    comments = api.request("GET", f"/api/tickets/{ticket_id}/comments")
    st.text(f"#{ticket['id']} · {ticket['title']}")
    st.text(
        f"{_label(ticket.get('status'))} · {_label(ticket.get('category'))} · "
        f"{_label(ticket.get('priority'))} · {ticket.get('suggestedTeam') or 'Unassigned team'}"
    )
    st.text(ticket["description"])
    if ticket.get("resolution"):
        st.text(f"Resolution: {ticket['resolution']}")
    if user["role"] in {"AGENT", "ADMIN"} and ticket.get("ai"):
        with st.expander("AI analysis (advisory)", expanded=True):
            st.text(ticket["ai"].get("summary") or "No AI summary is available.")
            if ticket["ai"].get("needsHuman"):
                st.text(ticket["ai"].get("reason") or "This incident needs human review.")
            st.text(
                f"Provider: {ticket['ai'].get('provider') or 'unknown'} · "
                f"Confidence: {ticket['ai'].get('confidence') if ticket['ai'].get('confidence') is not None else 'n/a'}"
            )

    st.markdown("#### Conversation")
    if comments:
        for comment in comments:
            with st.container(border=True):
                st.text(f"{comment['authorName']} · {_label(comment['authorRole'])}")
                if comment.get("internal"):
                    st.caption("Internal note")
                st.text(comment["body"])
    else:
        st.caption("No replies yet.")
    is_staff = user["role"] in {"AGENT", "ADMIN"}
    with st.form("comment_form", clear_on_submit=True):
        body = st.text_area("Reply", max_chars=4000)
        internal = st.checkbox("Internal note") if is_staff else False
        submitted = st.form_submit_button("Send reply")
    if submitted:
        api.request(
            "POST",
            f"/api/tickets/{ticket_id}/comments",
            body={"body": body, "internal": internal},
        )
        st.rerun()

    if is_staff:
        _render_staff_controls(api, ticket_id, ticket)


def _render_staff_controls(api: TicketIqApi, ticket_id: int, ticket: dict[str, Any]) -> None:
    st.markdown("#### Staff actions")
    status_column, triage_column = st.columns(2)
    with status_column:
        with st.form("status_form"):
            status = st.selectbox(
                "Update status",
                STATUSES,
                index=STATUSES.index(ticket["status"]) if ticket["status"] in STATUSES else 0,
            )
            resolution = st.text_area("Resolution / completion remarks", max_chars=4000)
            status_submitted = st.form_submit_button("Save status")
        if status_submitted:
            api.request(
                "PATCH",
                f"/api/tickets/{ticket_id}/status",
                body={"status": status, "resolution": resolution or None},
            )
            st.rerun()
    with triage_column:
        with st.form("triage_form"):
            category = st.selectbox(
                "Category",
                CATEGORIES,
                index=CATEGORIES.index(ticket["category"]) if ticket["category"] in CATEGORIES else 0,
            )
            priority = st.selectbox(
                "Priority",
                PRIORITIES,
                index=PRIORITIES.index(ticket["priority"]) if ticket["priority"] in PRIORITIES else 0,
            )
            triage_submitted = st.form_submit_button("Apply manual triage")
        if triage_submitted:
            api.request(
                "PATCH",
                f"/api/tickets/{ticket_id}/triage",
                body={"category": category, "priority": priority},
            )
            st.rerun()


def _render_insights(api: TicketIqApi) -> None:
    st.subheader("Insights")
    stats = api.request("GET", "/api/stats")
    ai_stats = stats.get("ai", {})
    total, ai, human = st.columns(3)
    total.metric("Total incidents", stats.get("totalTickets", 0))
    ai.metric("AI triaged", ai_stats.get("triagedByAi", 0))
    human.metric("Human triaged", ai_stats.get("triagedByHuman", 0))
    st.markdown("#### By status")
    st.json(stats.get("byStatus", {}))
    st.markdown("#### By category")
    st.json(stats.get("byCategory", {}))
    st.caption("AI suggestions are advisory; staff make the final support decisions.")


def main() -> None:
    st.set_page_config(page_title="TicketIQ", page_icon="🎫", layout="wide")
    st.title("TicketIQ")
    st.caption("Streamlit client for the TicketIQ Spring Boot support desk.")
    try:
        api_url = _api_url()
        api = TicketIqApi(api_url)
    except (ValueError, KeyError, FileNotFoundError) as error:
        st.error("TicketIQ is temporarily unavailable. Please try again later.")
        return

    session = st.session_state.get("ticketiq_session")
    if not session:
        try:
            _render_auth(api)
        except ApiError as error:
            st.error("TicketIQ is temporarily unavailable. Please try again later.")
        return

    user = session["user"]
    api = TicketIqApi(api_url, tokens=session["tokens"])
    navigation_target = st.session_state.pop("navigation_target", None)
    if navigation_target:
        st.session_state.page = navigation_target
    with st.sidebar:
        st.markdown(f"Signed in as **{user['fullName']}**")
        st.caption(_label(user["role"]))
        if st.button("Sign out"):
            st.session_state.pop("ticketiq_session", None)
            st.session_state.pop("ticket_filters", None)
            st.session_state.pop("selected_ticket_id", None)
            st.session_state.pop("page", None)
            st.rerun()
        pages = ["Incidents", "New incident", "Ticket details"]
        if user["role"] in {"AGENT", "ADMIN"}:
            pages.append("Insights")
        page = st.radio("Navigate", pages, key="page")

    try:
        if page == "Incidents":
            _render_ticket_list(api, user["role"] in {"AGENT", "ADMIN"})
        elif page == "New incident":
            _render_new_ticket(api)
        elif page == "Ticket details":
            _render_ticket_details(api, user)
        elif page == "Insights":
            _render_insights(api)
    except ApiError as error:
        if error.status_code == 401:
            st.session_state.pop("ticketiq_session", None)
            st.warning("Your session expired. Please sign in again.")
        else:
            st.error("TicketIQ is temporarily unavailable. Please try again later.")


if __name__ == "__main__":
    main()
