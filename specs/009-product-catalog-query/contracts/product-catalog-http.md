# HTTP Contract: Product Catalog Query

Base path exposed through api-gateway:

```text
/api/v1/catalog
```

## GET `/api/v1/catalog/categories`

Browse categories available to catalog readers.

### Query Parameters

| Name | Required | Meaning |
|------|----------|---------|
| `parentId` | no | UUID parent category id. Omit for root categories. |

### 200 Response

```json
{
  "data": [
    {
      "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
      "parentId": null,
      "slug": "phones",
      "name": "Phones",
      "sortOrder": 0
    }
  ]
}
```

## GET `/api/v1/catalog/products`

Browse visible products.

### Query Parameters

| Name | Required | Meaning |
|------|----------|---------|
| `categorySlug` | no | Existing category slug used to filter product membership. |
| `page` | no | Zero-based page number. Default `0`. |
| `size` | no | Page size from `1` to `100`. Default `20`. |

### 200 Response

```json
{
  "data": [
    {
      "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
      "code": "PROD-001",
      "slug": "iphone-15",
      "name": "iPhone 15",
      "shortDescription": "Apple smartphone",
      "variants": [
        {
          "id": "3370ff93-577c-4559-ac66-797a9f4bb499",
          "sku": "IPHONE-15-BLACK",
          "name": "Black",
          "basePrice": "19990000.0000",
          "currency": "VND"
        }
      ]
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

### 404 Response

Returned when `categorySlug` does not exist.

```json
{
  "code": "CATEGORY_NOT_FOUND",
  "message": "Category was not found"
}
```

## GET `/api/v1/catalog/products/{slug}`

View visible product detail.

### 200 Response

```json
{
  "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
  "code": "PROD-001",
  "slug": "iphone-15",
  "name": "iPhone 15",
  "shortDescription": "Apple smartphone",
  "description": "Long description",
  "variants": [
    {
      "id": "3370ff93-577c-4559-ac66-797a9f4bb499",
      "sku": "IPHONE-15-BLACK",
      "name": "Black",
      "basePrice": "19990000.0000",
      "currency": "VND"
    }
  ],
  "categories": [
    {
      "id": "b0be67d2-dabb-40ef-a239-75d6ff0be6cc",
      "slug": "phones",
      "name": "Phones",
      "primary": true,
      "sortOrder": 0
    }
  ],
  "media": [
    {
      "id": "e6ffef17-82c1-4c0c-a110-b26aac2f3036",
      "mediaType": "IMAGE",
      "url": "https://cdn.example.test/iphone.webp",
      "altText": "iPhone 15",
      "sortOrder": 0
    }
  ]
}
```

### 404 Response

Returned when the product does not exist or is not visible.

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "Product was not found"
}
```

## Validation Errors

### 400 Response

Returned when paging parameters are outside accepted bounds.

```json
{
  "code": "INVALID_CATALOG_REQUEST",
  "message": "Page size must be between 1 and 100"
}
```

## Compatibility

This is the first version of the catalog read contract. Future changes must preserve existing fields or introduce a versioned path/contract update.
