# Catalog Discovery Research

## Findings

- Shopify's Storefront Search and Search & Discovery model combines search with
  common filters such as availability, category, price, product type, tags, and
  vendor. It also supports configurable filters and predictive search. This
  supports making search/filter state explicit rather than filtering a large local
  array in the browser.
- Shopify collections can be manual or rule-based. That is a useful future model
  for curated storefront groupings, but this MVP only needs the existing category
  hierarchy and does not add a collection engine.
- Shopee's seller education material shows a shop page organized by custom shop
  categories with common sorting such as popularity, latest, top sales, and price.
  That validates category navigation and deterministic sort as high-value shopper
  functionality.
- Shopee's marketplace also exposes seller/shop labels and seller-level filters.
  That is a marketplace concern and should not be introduced into this single-store
  architecture without a separate seller domain and ownership decision.

## Decision

Implement a Catalog Discovery MVP around the existing Product Service:

1. Extend the existing public products query with `q`, price bounds, `sort`, and
   category slug while keeping old calls valid.
2. Make category selection and search URL-driven in QuickCart.
3. Keep inventory availability out of the query until a measured projection or
   documented cross-service contract exists.
4. Defer stores/sellers, collections, autocomplete, and recommendations.

## References

- [Shopify Search & Discovery filters](https://help.shopify.com/en/manual/online-store/storefront-search/search-and-discovery-filters)
- [Shopify storefront search](https://help.shopify.com/en/manual/online-store/storefront-search)
- [Shopify collections](https://help.shopify.com/en/manual/products/collections/create-collection)
- [Shopee shop categories and sorting](https://cdngarenanow-a.akamaihd.net/shopee/seller/seller_cms/f92e0cabf4fe7ed588e80c6988c90932/%5BMY%5D%20Shopee%20Uni%20Basic%20-%20ENG.pdf)
- [Shopee shop/Mall filtering](https://help.shopee.com.my/portal/4/article/78711)

