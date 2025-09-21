import { createFileRoute } from "@tanstack/react-router";
import { api } from "shared/api";
import { Button } from "src/components/ui/button";
import { local } from "src/shared/local";
import z from "zod";

const Login = () => {
    const search = Route.useSearch();

    async function handleSignIn() {
        if (search.dest) {
            local.loginDest.set(search.dest);
        } else {
            local.loginDest.clear();
        }

        const { url } = await api.getOAuthUrl("google");
        window.location.href = url;
    }

    return (
        <div className="flex items-center justify-center min-h-screen">
            <div className="text-center space-y-4">
                <h1 className="text-2xl font-bold">Welcome to Lesson Archiver</h1>
                <p className="text-gray-600">Please sign in to continue</p>
                <Button onClick={handleSignIn}>Sign In</Button>
            </div>
        </div>
    );
};

export const Route = createFileRoute("/login")({
    component: Login,
    validateSearch: z.object({ dest: z.string().optional() }),
});
