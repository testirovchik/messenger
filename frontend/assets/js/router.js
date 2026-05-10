export function navigateTo(path) {
    window.location.href = path.startsWith('/') ? path : '/' + path;
}