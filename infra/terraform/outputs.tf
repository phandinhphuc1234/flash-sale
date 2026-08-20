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
