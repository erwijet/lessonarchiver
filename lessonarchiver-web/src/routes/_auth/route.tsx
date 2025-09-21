import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { maybe } from "src/shared/fp";
import { local } from "src/shared/local";
import { parseToken, SessionRenewer } from "src/shared/token";

const Page = () => {
    return (
        <>
            <SessionRenewer />
            <Outlet />
        </>
    );
};

export const Route = createFileRoute("/_auth")({
    component: Page,
    beforeLoad() {
        const session = maybe(local.token.get())?.take(parseToken);
        if (!!session && !session.isExpired()) {
            return { session };
        }

        throw redirect({ to: "/logout", search: { dest: window.location.pathname } });
    },
});
