import { createFileRoute, Outlet, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { Button } from "src/components/ui/button";
import { Spinner } from "src/components/ui/shadcn-io/spinner";

const Login = () => {
    const auth = useAuth();
    const navigate = useNavigate();

    useEffect(() => {
        if (auth.isAuthenticated) {
            navigate({ to: "/" });
        }
    }, [auth.isAuthenticated]);

    if (auth.isLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <Spinner />
            </div>
        );
    }

    if (auth.isAuthenticated) {
        return <Outlet />;
    }

    const handleSignIn = () => {
        auth.signinRedirect();
    };

    return (
        <div className="flex items-center justify-center min-h-screen">
            <div className="text-center space-y-4">
                <h1 className="text-2xl font-bold">Welcome to LessonBinder</h1>
                <p className="text-gray-600">Please sign in to continue</p>
                <Button onClick={handleSignIn} disabled={auth.isLoading}>
                    {auth.isLoading ? "Signing in..." : "Sign In"}
                </Button>
                <Button variant="ghost" onClick={function() {
                    auth.signoutRedirect();
                }}>Logout</Button>
                {auth.error && (
                    <div className="text-red-600 mt-2">
                        <p>Sign in failed: {auth.error.message}</p>
                    </div>
                )}
            </div>
        </div>
    );
};

export const Route = createFileRoute("/login")({
    component: Login,
});
