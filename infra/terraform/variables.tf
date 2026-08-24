variable "aws_region" {
  description = "AWS region that hosts the development platform."
  type        = string
  default     = "ap-southeast-2"

  validation {
    condition     = length(trimspace(var.aws_region)) > 0
    error_message = "aws_region must not be empty."
  }
}

variable "project_name" {
  description = "Project identifier applied to shared AWS resources."
  type        = string
  default     = "flash-sale"

  validation {
    condition     = length(trimspace(var.project_name)) > 0
    error_message = "project_name must not be empty."
  }
}

variable "environment" {
  description = "Deployment environment applied to shared AWS resources."
  type        = string
  default     = "dev"

  validation {
    condition     = length(trimspace(var.environment)) > 0
    error_message = "environment must not be empty."
  }
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "Explicit IPv4 CIDRs allowed to reach the public EKS API endpoint, normally the operator's current public IP as /32."
  type        = list(string)

  validation {
    condition = (
      length(var.cluster_endpoint_public_access_cidrs) > 0 &&
      alltrue([
        for cidr in var.cluster_endpoint_public_access_cidrs :
        can(cidrhost(cidr, 0)) && length(regexall(":", cidr)) == 0
      ]) &&
      !contains(var.cluster_endpoint_public_access_cidrs, "0.0.0.0/0")
    )
    error_message = "Supply at least one valid IPv4 CIDR and do not use 0.0.0.0/0. Use the operator's public IP as /32 whenever possible."
  }
}

variable "gateway_acm_enabled" {
  description = "Create an ACM DNS-validated certificate for the optional HTTPS development Gateway edge."
  type        = bool
  default     = false
}

variable "gateway_domain_name" {
  description = "DNS name for the HTTPS development Gateway edge, for example api-dev.example.com."
  type        = string
  default     = ""

  validation {
    condition = (
      trimspace(var.gateway_domain_name) == "" ||
      can(regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$", trimspace(var.gateway_domain_name)))
    )
    error_message = "gateway_domain_name must be a DNS name or remain empty while ACM is disabled."
  }
}
