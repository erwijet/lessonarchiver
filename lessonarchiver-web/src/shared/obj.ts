export const obj = {
    with<Source extends object, const K extends string, V>(r: Record<K, V>) {
        return function (o: Source): Omit<Source, K> & Record<K, V> {
            const copy = { ...o } as Omit<Source, K> & Record<K, V>;
            for (const [k, v] of Object.entries(r)) {
                (copy as Record<K, V>)[k as K] = v as V;
            }

            return copy;
        };
    },
    omit<Source, K extends keyof Source>(...keys: K[]) {
        return function (o: Source): Omit<Source, K> {
            const copy = { ...o };
            for (const k of keys) delete copy[k];
            return copy;
        };
    },
    pick<Source extends object, K extends keyof Source>(...keys: K[]) {
        return function (o: Source): Pick<Source, K> {
            const next: Partial<Pick<Source, K>> = {};
            keys.forEach((k) => {
                next[k] = o[k];
            });

            return next as Pick<Source, K>;
        };
    },
    $<Source extends object, K extends keyof Source>(k: K) {
        return function (o: Source) {
            return o[k];
        };
    },
    of<Source extends object>(o: Source) {
        return {
            pick<K extends keyof Source>(...keys: K[]) {
                return obj.pick<Source, K>(...keys)(o);
            },
            omit<K extends keyof Source>(...keys: K[]) {
                return obj.omit<Source, K>(...keys)(o);
            },
            $<K extends keyof Source>(k: K) {
                return obj.$<Source, K>(k)(o);
            },
            with<K extends string, V>(r: Record<K, V>) {
                return obj.with(r)(o);
            },
        };
    },
};
