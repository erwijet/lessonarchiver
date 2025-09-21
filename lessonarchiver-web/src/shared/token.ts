import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { api } from "shared/api";
import { runCatching } from "shared/fn";
import { just } from "shared/fp";
import { local } from "shared/local";
import z from "zod";

export function parseToken(token: string) {
    return just(token)
        .map((it) => runCatching(() => parseJwt(it)))
        ?.take(
            z
                .object({
                    family_name: z.string(),
                    fullname: z.string(),
                    given_name: z.string(),
                    nbf: z.number(),
                    exp: z.number(),
                })
                .transform((it) => ({
                    ...it,
                    isExpired: () => Date.now() / 1000 > it.exp || Date.now() / 1000 < it.nbf,
                })).parse,
        );
}

function parseJwt(token: string): unknown {
    const base64Url = token.split(".")[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
        window
            .atob(base64)
            .split("")
            .map(function (c) {
                return "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2);
            })
            .join(""),
    );

    return JSON.parse(jsonPayload);
}

export const SessionRenewer = () => {
    const { data } = useQuery({
        queryKey: ["session-renewer"],
        refetchInterval: 2 * 60 * 1000, // 2 minutes
        refetchIntervalInBackground: true,
        queryFn: () => api.renewSession(),
    });

    useEffect(() => {
        if (!data?.token) return;
        local.token.set(data.token);
    }, [data]);

    return null;
};
