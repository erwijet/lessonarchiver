import { toast } from "sonner";
import { createStore } from "zustand";

type Factory<T> = () => T;

// biome-ignore lint: typealias defined for extension
type AnyFn = (...args: any[]) => any;

/**
 * Runs the given function, absorbing any errors if thrown
 */
export function runCatching<R>(f: () => R): R | undefined {
    try {
        return f();
    } catch {}
}

/**
 * Creates a lazy-evaluated function that caches the result after the first call
 */
export const createLazy = <T>(fn: () => T): Factory<T> => {
    let result: T | undefined;
    let evaluated = false;

    return () => {
        if (!evaluated) {
            result = fn();
            evaluated = true;
        }
        return result!;
    };
};

/**
 * Constructs a filter predicate which dedups an array of objects by some given key
 */
export function uniqueBy<T extends object, K extends keyof T>(k: K): (t: T) => boolean;
export function uniqueBy<T extends object, S>(select: (t: T) => S): (t: T) => boolean;
export function uniqueBy<T extends object, K extends keyof T, S>(disc: K | ((t: T) => S)): (t: T) => boolean {
    const seen = new Set<T[K] | S>();
    return (item: T) => {
        const selected = typeof disc == "function" ? disc(item) : item[disc];
        if (seen.has(selected)) return false;
        seen.add(selected);
        return true;
    };
}

/**
 * Constructs a debounce facilitator for executing anonymous, arbitrary blocks
 *
 * @example
 * ```ts
 * const { debouncing } = createDebounce();
 *
 * function handleClick() {
 *      console.log("this will always run")
 *      debouncing(() => {
 *          // this part is debounced!
 *      })
 * }
 * ```
 */
export function createDebounce(delayMs = 300) {
    const s = createStore<{ lastExecTime: number | null; timeoutId: number | null; didExec: () => void }>()((set) => ({
        lastExecTime: null,
        timeoutId: null,
        didExec: () => set({ lastExecTime: Date.now(), timeoutId: null }),
    }));

    return {
        debouncing: function (fn: () => void) {
            const now = Date.now();
            const { lastExecTime, timeoutId, didExec } = s.getState();

            if (lastExecTime && now - lastExecTime < delayMs) {
                if (timeoutId) {
                    clearTimeout(timeoutId);
                }

                s.setState({
                    timeoutId: +setTimeout(
                        () => {
                            fn();
                            didExec();
                        },
                        delayMs - (now - lastExecTime),
                    ),
                });
            } else {
                fn();
                didExec();
            }
        },
    };
}

export function fail<R = never>(err: unknown): NonNullable<R> {
    if (err instanceof Error) throw err;
    throw new Error(JSON.stringify(err));
}

/** Attaches a standard catch handler to the given promise, absorbing any other values */
export function runAsync(p: Promise<unknown> | Factory<Promise<unknown>>): void {
    (typeof p == "function" ? p() : p).catch(toast.error);
}

export function run<F extends AnyFn>(f: F): ReturnType<F> {
    return f();
}

export function voiding<F extends AnyFn>(f: F): (...args: Parameters<F>) => void {
    return (...args: Parameters<F>) => {
        f(...args);
    };
}

export function titlecase(s: string) {
    return s
        .split(/\s/)
        .map((each) => each.at(0)?.toLocaleUpperCase() + each.slice(1).toLocaleLowerCase())
        .join(" ");
}
