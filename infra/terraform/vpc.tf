module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.21.0"

  name = "flash-sale-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["ap-southeast-2a", "ap-southeast-2b"]
  public_subnets  = ["10.0.0.0/24", "10.0.1.0/24"]
  private_subnets = ["10.0.16.0/20", "10.0.32.0/20"]

  enable_nat_gateway = true
  # One NAT gateway is an intentional cost-saving choice for the development environment.
  single_nat_gateway     = true
  one_nat_gateway_per_az = false

  public_subnet_tags = {
    # Lets the AWS Load Balancer Controller discover internet-facing load balancer subnets.
    "kubernetes.io/role/elb" = "1"
  }

  private_subnet_tags = {
    # Lets the AWS Load Balancer Controller discover internal load balancer subnets.
    "kubernetes.io/role/internal-elb" = "1"
  }
}
