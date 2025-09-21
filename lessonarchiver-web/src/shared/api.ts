import { QueryClient } from "@tanstack/react-query";
import { maybe } from "shared/fp";
import { local } from "shared/local";
import { obj } from "shared/obj";
import { z } from "zod";

const transport = {
    async http(path: string, opts: RequestInit = {}) {
        const result = await fetch(import.meta.env["VITE_API_BASE"] + path, {
            ...opts,
            headers:
                maybe(local.token.get())?.take((token) => obj.of(opts.headers ?? {}).with({ Authorization: "Bearer " + token })) ??
                opts.headers,
        });

        if (result.status == 401) {
            // window.location.href = "/logout";
        }

        if (!result.ok) {
            console.error(await result.text());
        }

        return result.json();
    },

    get(path: string, opts?: RequestInit) {
        return this.http(path, opts);
    },
    getPaginated(path: string, page: Page, opts?: RequestInit) {
        const [justPath, params = ""] = path.split("?");
        const search = new URLSearchParams(params);
        search.append("limit", page.limit.toString());
        search.append("offset", page.offset.toString());
        return this.get(justPath + "?" + search.toString(), opts);
    },
    post(path: string, body: BodyInit, opts?: RequestInit) {
        return this.http(path, {
            method: "POST",
            body,
            ...opts,
        });
    },
};

type Page = {
    limit: number;
    offset: number;
};

export const api = {
    client: new QueryClient({}),
    async getOAuthUrl(provider: "google"): Promise<GetOAuthUrlResult> {
        const data = await transport.get(`/auth/${provider}?${new URLSearchParams([["env", import.meta.env.VITE_ENVIRONMENT]])}`);
        return zGetOAuthUrlResult.parse(data);
    },
    async renewSession(): Promise<RenewSessionResult> {
        const data = await transport.get("/auth/renew");
        return zRenewSessionResult.parse(data);
    },
    async getFiles(page: Page): Promise<GetFilesResult> {
        const data = await transport.getPaginated("/files", page);
        return zGetFilesResult.parse(data);
    },
};

const zGetOAuthUrlResult = z.object({
    url: z.string(),
});
type GetOAuthUrlResult = z.infer<typeof zGetOAuthUrlResult>;

const zRenewSessionResult = z.object({
    token: z.string(),
});
type RenewSessionResult = z.infer<typeof zRenewSessionResult>;

const zGetFilesResult = z
    .object({
        id: z.string(),
        fileName: z.string(),
        contentLength: z.coerce.number(),
        sha1: z.hash("sha1"),
        uploadedAt: z.coerce.date(),
    })
    .array();
export type GetFilesResult = z.infer<typeof zGetFilesResult>;
