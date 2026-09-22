export function suggestCampaignCode(name, startAt, suffix) {
  if (!suffix) return "";

  const slug = name.normalize("NFKD")
    .replace(/\p{M}/gu, "")
    .replace(/[đĐ]/g, "D")
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "FLASH";
  const date = startAt.slice(0, 10).replace(/-/g, "");
  const ending = `${date ? `-${date}` : ""}-${suffix}`;
  const prefix = slug.slice(0, 64 - ending.length).replace(/-+$/g, "");

  return `${prefix}${ending}`;
}
