import { useSuspenseInfiniteQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { Table, TableBody, TableHead, TableHeader, TableRow } from "components/ui/table";
import { ArrowDown } from "lucide-react";
import { useEffect } from "react";
import { toast } from "sonner";
import { Button } from "src/components/ui/button";
import { api, GetFilesResult } from "src/shared/api";

const Page = () => {
    const { session } = Route.useRouteContext();
    const { data, fetchNextPage, hasNextPage } = useSuspenseInfiniteQuery<GetFilesResult>({
        queryKey: ["files"],
        initialPageParam: 0,
        getNextPageParam: (lastPage, pages, _) => (lastPage.length == 0 ? null : pages.flat().length),
        queryFn: ({ pageParam: offset }) => api.getFiles({ limit: 1, offset: offset as number }),
    });

    useEffect(() => {
        toast.success("Welcome");
    }, []);

    return (
        <div className="flex flex-col">
            <Table>
                <TableHead>
                    <TableRow>
                        <TableHeader>each</TableHeader>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {data.pages.map((page) =>
                        page.map((each) => (
                            <TableRow key={each.id}>
                                <pre>{JSON.stringify(each)}</pre>
                            </TableRow>
                        )),
                    )}
                </TableBody>
            </Table>
            <div className="flex">
                <p>Total Rows: {data.pages.flat().length}</p>
                <Button onClick={() => fetchNextPage()} disabled={!hasNextPage}>
                    <ArrowDown />
                    Load More
                </Button>
            </div>
        </div>
    );
};

export const Route = createFileRoute("/_auth/about")({
    component: Page,
});
