export default function ApiNotice({ error, className = "" }) {
  if (!error) return null;
  const fields = error.errors?.map((item) => item.message).filter(Boolean).join(" ");
  return <p role="alert" className={`rounded bg-red-50 px-3 py-2 text-sm text-red-700 ${className}`}>{fields || error.message || "Có lỗi xảy ra. Vui lòng thử lại."}</p>;
}
