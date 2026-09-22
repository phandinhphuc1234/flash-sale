const textOrEmpty = (value) => typeof value === "string" ? value.trim() : "";

/** Keep only the fields that the account UI is allowed to render. */
export function normalizeAccountProfile(value) {
  if (!value || typeof value !== "object") return null;
  const email = textOrEmpty(value.email);
  const username = textOrEmpty(value.username);
  const login = textOrEmpty(value.login) || username || email;
  if (!login) return null;
  const fullName = textOrEmpty(value.fullName);
  const displayName = fullName || textOrEmpty(value.displayName) || username || login;
  const authorities = Array.isArray(value.authorities)
    ? value.authorities.filter((authority) => typeof authority === "string")
    : [];
  return {
    id: textOrEmpty(value.id),
    login,
    username,
    email,
    displayName,
    fullName,
    phone: textOrEmpty(value.phone),
    address: textOrEmpty(value.address),
    status: textOrEmpty(value.status) || "ACTIVE",
    authorities,
  };
}

export function accountInitials(profile) {
  const value = profile?.displayName || profile?.login || "A";
  return value.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join("") || "A";
}
