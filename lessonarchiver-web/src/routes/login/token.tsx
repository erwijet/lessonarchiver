import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { Spinner } from "src/components/ui/shadcn-io/spinner";

const LoginCallback = () => {
    const auth = useAuth();
    const navigate = useNavigate();

    useEffect(() => {
        if (auth.isAuthenticated) {
            // Redirect to home or intended route after successful login
            navigate({ to: "/" });
        }
    }, [auth.isAuthenticated, navigate]);

    if (auth.error) {
        return (
            <div className="p-4">
                <h2>Authentication Error</h2>
                <p>{auth.error.message}</p>
                <button onClick={() => auth.signinRedirect()}>Try Again</button>
            </div>
        );
    }

    return (
        <div className="flex items-center justify-center min-h-screen">
            <Spinner />
            <span className="ml-2">Completing sign in...</span>
        </div>
    );
};

export const Route = createFileRoute("/login/token")({
    component: LoginCallback,
});
