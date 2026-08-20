module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "20.37.2"

  cluster_name                         = "flash-sale-dev"
  cluster_version                      = "1.36"
  cluster_endpoint_private_access      = true
  cluster_endpoint_public_access       = true
  cluster_endpoint_public_access_cidrs = var.cluster_endpoint_public_access_cidrs

  # Worker nodes stay private; the public subnets remain available to future load balancers.
  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Bootstrap access for the Terraform caller; later GitOps roles are managed separately.
  enable_cluster_creator_admin_permissions = true
  # This project uses a conventional managed node group, not EKS Auto Mode.
  enable_auto_mode_custom_tags = false

  # Simple internship baseline: three on-demand workers, with limited manual scaling headroom.
  eks_managed_node_groups = {
    general = {
      instance_types = ["m7i-flex.large"]
      capacity_type  = "ON_DEMAND"
      min_size       = 2
      max_size       = 4
      desired_size   = 3
    }
  }
}
