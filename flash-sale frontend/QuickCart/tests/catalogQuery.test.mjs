import test from "node:test";
import assert from "node:assert/strict";
import { buildCatalogProductsPath } from "../lib/catalogQuery.mjs";

test("serializes the public catalog discovery contract", () => {
  assert.equal(
    buildCatalogProductsPath({
      q: "  headphones ",
      categorySlug: " electronics ",
      minPrice: 100000,
      maxPrice: 500000,
      sort: "PRICE_ASC",
      page: 1,
      size: 20,
    }),
    "/api/v1/catalog/products?page=1&size=20&sort=PRICE_ASC&q=headphones&categorySlug=electronics&minPrice=100000&maxPrice=500000",
  );
});

test("omits optional filters and preserves bounded defaults", () => {
  assert.equal(
    buildCatalogProductsPath(),
    "/api/v1/catalog/products?page=0&size=20&sort=NEWEST",
  );
});
