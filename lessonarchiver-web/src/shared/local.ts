export const local = {
    token: createLocalSlice("com.lessonarchiver.auth.token"),
    loginDest: createLocalSlice("com.lessonarchiver.auth.login-destination"),
};

function createLocalSlice(key: string) {
    return {
        key,
        get: () => localStorage.getItem(key),
        set: (value: string) => localStorage.setItem(key, value),
        clear: () => localStorage.removeItem(key),
    };
}
