type ProductCardProps = {
  rank: number;
  score: number;
  title: string;
  summary: string;
  meta: string;
  reason: string;
  actionLabel?: string;
  onAction?: () => void;
  actionLoading?: boolean;
};

function ProductCard({
  rank,
  score,
  title,
  summary,
  meta,
  reason,
  actionLabel,
  onAction,
  actionLoading
}: ProductCardProps) {
  return (
    <li className="product-card">
      <div className="card-top">
        <p className="rank">TOP {rank}</p>
        <p className="score">{score}점</p>
      </div>
      <h4>{title}</h4>
      <p className="summary">{summary}</p>
      <p className="meta">{meta}</p>
      <p className="reason">{reason}</p>
      {onAction ? (
        <button
          type="button"
          className="card-action"
          disabled={Boolean(actionLoading)}
          onClick={onAction}
        >
          {actionLoading ? "이동 준비 중..." : actionLabel ?? "공식 사이트 이동"}
        </button>
      ) : null}
    </li>
  );
}

export default ProductCard;
