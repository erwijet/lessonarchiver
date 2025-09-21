import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { local } from "src/shared/local";
import z from "zod";

const LoginCallback = () => {
    const { token } = Route.useSearch();

    useEffect(() => {
        local.token.set(token);
        window.location.href = local.loginDest.get() ?? "/";
    }, []);

    return "Loading...";
};

export const Route = createFileRoute("/token")({
    component: LoginCallback,
    validateSearch: z.object({ token: z.string() }),
});
