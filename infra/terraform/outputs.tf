output "aws_region" {
  description = "AWS region hosting the development platform."
  value       = var.aws_region
}

output "cluster_name" {
  description = "Name used to configure kubectl and later GitOps workflows."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS Kubernetes API endpoint."
  value       = module.eks.cluster_endpoint
}

output "private_subnet_ids" {
  description = "Private subnet IDs used by the EKS managed node group."
  value       = module.vpc.private_subnets
}

output "ecr_repository_urls" {
  description = "ECR repository URLs keyed by service name."
  value = {
    for service_name, repository in aws_ecr_repository.repo :
    service_name => repository.repository_url
  }
}

output "gateway_acm_certificate_arn" {
  description = "ACM certificate ARN for the optional HTTPS Gateway edge; null when disabled."
  value       = try(aws_acm_certificate.gateway[0].arn, null)
}

output "gateway_acm_dns_validation_options" {
  description = "DNS validation records to publish for the optional ACM certificate."
  value = try([
    for option in aws_acm_certificate.gateway[0].domain_validation_options : {
      domain_name           = option.domain_name
      resource_record_name  = option.resource_record_name
      resource_record_type  = option.resource_record_type
      resource_record_value = option.resource_record_value
    }
  ], [])
}
