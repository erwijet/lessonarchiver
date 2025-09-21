import { createRouter, RouterProvider } from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { routeTree } from "src/routeTree.gen";

import "src/index.css";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "components/ui/sonner";
import { api } from "src/shared/api";

const router = createRouter({ routeTree });

declare module "@tanstack/react-router" {
    interface Register {
        router: typeof router;
    }
}

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <QueryClientProvider client={api.client}>
            <Toaster />
            <RouterProvider router={router} />
        </QueryClientProvider>
    </StrictMode>,
);
