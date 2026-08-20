# Initial GitOps catalog: the eight services currently implemented by the project.
variable "service_names" {
  type = list(string)
  default = [
    "api-gateway",
    "authentication-service",
    "product-service",
    "campaign-service",
    "flash-sale-service",
    "inventory-service",
    "order-service",
    "payment-service"
  ]
}

resource "aws_ecr_repository" "repo" {
  for_each             = toset(var.service_names)
  name                 = "flash-sale/${each.value}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}
