# Optional ACM certificate for the development Gateway edge.
# Disabled by default so existing EKS/Argo applies remain private and unchanged.
# DNS validation records must be created in the DNS provider that owns the domain.
resource "aws_acm_certificate" "gateway" {
  count = var.gateway_acm_enabled ? 1 : 0

  domain_name               = var.gateway_domain_name
  subject_alternative_names = ["*.${var.gateway_domain_name}"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
    precondition {
      condition     = length(trimspace(var.gateway_domain_name)) > 0
      error_message = "gateway_domain_name is required when gateway_acm_enabled is true."
    }
  }
}
