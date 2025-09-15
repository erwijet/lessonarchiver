import { createRouter, RouterProvider } from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { routeTree } from "src/routeTree.gen";

import "src/index.css";
import { AuthProvider } from "react-oidc-context";

const router = createRouter({ routeTree });

declare module "@tanstack/react-router" {
    interface Registrar {
        router: typeof router;
    }
}

const oidc = {
    authority: "https://sso.lessonbinder.com/realms/lessonbinder",
    client_id: "lb-web",
    redirect_uri: window.location.origin + "/login/callback",
    post_logout_redirect_uri: window.location.origin + "/login",
    scope: "openid profile email",
};

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <AuthProvider {...oidc}>
            <RouterProvider router={router} />
        </AuthProvider>
    </StrictMode>,
);
