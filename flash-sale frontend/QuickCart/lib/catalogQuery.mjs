export function buildCatalogProductsPath({
  q,
  categorySlug,
  minPrice,
  maxPrice,
  sort = "NEWEST",
  page = 0,
  size = 20,
} = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort });
  if (q?.trim()) params.set("q", q.trim());
  if (categorySlug?.trim()) params.set("categorySlug", categorySlug.trim());
  if (minPrice != null && minPrice !== "") params.set("minPrice", String(minPrice));
  if (maxPrice != null && maxPrice !== "") params.set("maxPrice", String(maxPrice));
  return `/api/v1/catalog/products?${params.toString()}`;
}
