import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { local } from "shared/local";
import z from "zod";

function Page() {
    const nav = useNavigate();
    const search = Route.useSearch();

    useEffect(() => {
        local.token.clear();
        nav({
            to: "/login",
            search,
        });
    }, []);

    return "Loading...";
}

export const Route = createFileRoute("/logout")({
    component: Page,
    validateSearch: z.object({ dest: z.string().optional() }),
});
