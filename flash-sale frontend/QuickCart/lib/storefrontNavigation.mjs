export const STOREFRONT_DESTINATIONS = Object.freeze({
  HOME: "home",
  SHOP: "shop",
  FLASH_SALE: "flash-sale",
  CART: "cart",
});

function normalizePathname(pathname) {
  if (typeof pathname !== "string" || !pathname.trim()) return "";

  const path = pathname.trim().split(/[?#]/, 1)[0];
  if (!path.startsWith("/")) return "";
  if (path === "/") return path;

  return path.replace(/\/+$/, "");
}

function matchesRouteFamily(pathname, root) {
  return pathname === root || pathname.startsWith(`${root}/`);
}

export function classifyStorefrontRoute(pathname) {
  const normalizedPath = normalizePathname(pathname);

  if (normalizedPath === "/") return STOREFRONT_DESTINATIONS.HOME;
  if (
    matchesRouteFamily(normalizedPath, "/all-products") ||
    matchesRouteFamily(normalizedPath, "/product")
  ) {
    return STOREFRONT_DESTINATIONS.SHOP;
  }
  if (matchesRouteFamily(normalizedPath, "/flash-sale")) {
    return STOREFRONT_DESTINATIONS.FLASH_SALE;
  }
  if (matchesRouteFamily(normalizedPath, "/cart")) {
    return STOREFRONT_DESTINATIONS.CART;
  }

  return null;
}
