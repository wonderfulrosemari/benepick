import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  RecommendationBundleBenefitComponent,
  RecommendationDetailField,
  RecommendationItem
} from "../types/recommendation";

type ProductCardProps = {
  rank: number;
  productType: RecommendationItem["productType"];
  title: string;
  summary: string;
  meta: string;
  reason: string;
  minExpectedMonthlyBenefit: number;
  expectedMonthlyBenefit: number;
  maxExpectedMonthlyBenefit: number;
  benefitComponents?: RecommendationBundleBenefitComponent[];
  detailFields?: RecommendationDetailField[];
  actionLabel?: string;
  onAction?: () => void;
  actionLoading?: boolean;
  highlightKeywords?: string[];
  clickCount?: number;
  clickRatePercent?: number;
};

const EMPHASIS_NUMBER_SPLIT_REGEX = /(최고\s*\d+(?:\.\d+)?\s*%|기본\s*\d+(?:\.\d+)?\s*%|월\s*최대\s*\d[\d,]*(?:\.\d+)?\s*(?:만원|원)|최대\s*\d[\d,]*(?:\.\d+)?\s*(?:만원|원)|\d+(?:\.\d+)?\s*%|\+?\d[\d,]*(?:\.\d+)?\s*(?:만원|원))/g;
const EMPHASIS_NUMBER_TEST_REGEX = /^(최고\s*\d+(?:\.\d+)?\s*%|기본\s*\d+(?:\.\d+)?\s*%|월\s*최대\s*\d[\d,]*(?:\.\d+)?\s*(?:만원|원)|최대\s*\d[\d,]*(?:\.\d+)?\s*(?:만원|원)|\d+(?:\.\d+)?\s*%|\+?\d[\d,]*(?:\.\d+)?\s*(?:만원|원))$/;
const EMPHASIS_KEYWORD_SPLIT_REGEX = /(가입대상|가입조건|우대조건|우대|조건|연회비|혜택|할인|캐시백|적립|한도|전월실적|급여이체|저축|금리|최고|기본|최대|최소)/g;
const EMPHASIS_KEYWORD_TEST_REGEX = /^(가입대상|가입조건|우대조건|우대|조건|연회비|혜택|할인|캐시백|적립|한도|전월실적|급여이체|저축|금리|최고|기본|최대|최소)$/;
const PARAGRAPH_DELIMITER_REGEX = /\s*[·•]\s*/g;
const NUMBERING_BREAK_REGEX = /\s+(?=(?:\d+|[가-힣])\.\s)/g;
const READABLE_DETAIL_LABELS = new Set(["핵심 설명", "핵심 혜택", "정량 혜택"]);

type HighlightMatchers = {
  splitRegex: RegExp;
  exactRegex: RegExp;
};

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function buildHighlightMatchers(highlightKeywords?: string[]): HighlightMatchers | null {
  const normalizedKeywords = Array.from(
    new Set(
      (highlightKeywords ?? [])
        .map((keyword) => keyword.trim())
        .filter((keyword) => keyword.length >= 2)
    )
  );

  if (normalizedKeywords.length === 0) {
    return null;
  }

  const escaped = normalizedKeywords
    .map((keyword) => escapeRegExp(keyword))
    .sort((left, right) => right.length - left.length);

  return {
    splitRegex: new RegExp(`(${escaped.join("|")})`, "gi"),
    exactRegex: new RegExp(`^(?:${escaped.join("|")})$`, "i")
  };
}

function renderTextWithHighlight(
  text: string,
  keyPrefix: string,
  highlightMatchers: HighlightMatchers | null
): ReactNode {
  if (!highlightMatchers) {
    return <span key={`${keyPrefix}-plain`}>{text}</span>;
  }

  return text.split(highlightMatchers.splitRegex).map((part, index) => {
    if (!part) {
      return null;
    }

    if (highlightMatchers.exactRegex.test(part)) {
      return (
        <strong key={`${keyPrefix}-hit-${index}`} className="text-emphasis-filter">
          {part}
        </strong>
      );
    }

    return <span key={`${keyPrefix}-txt-${index}`}>{part}</span>;
  });
}

function renderEmphasizedText(
  text: string,
  keyPrefix: string,
  highlightMatchers: HighlightMatchers | null
): ReactNode {
  const normalized = text?.trim();
  if (!normalized) {
    return text;
  }

  return normalized
    .split(EMPHASIS_NUMBER_SPLIT_REGEX)
    .flatMap((numberPart, numberIndex) => {
      if (!numberPart) {
        return [];
      }

      if (EMPHASIS_NUMBER_TEST_REGEX.test(numberPart)) {
        return (
          <strong
            key={`${keyPrefix}-num-${numberIndex}`}
            className="text-emphasis-number"
          >
            {numberPart}
          </strong>
        );
      }

      return numberPart.split(EMPHASIS_KEYWORD_SPLIT_REGEX).map((keywordPart, keywordIndex) => {
        if (!keywordPart) {
          return null;
        }

        if (EMPHASIS_KEYWORD_TEST_REGEX.test(keywordPart)) {
          const isFilterMatch = highlightMatchers?.exactRegex.test(keywordPart) ?? false;
          return (
            <span
              key={`${keyPrefix}-kw-${numberIndex}-${keywordIndex}`}
              className={isFilterMatch ? "text-emphasis-keyword text-emphasis-filter" : "text-emphasis-keyword"}
            >
              {keywordPart}
            </span>
          );
        }

        return renderTextWithHighlight(
          keywordPart,
          `${keyPrefix}-txt-${numberIndex}-${keywordIndex}`,
          highlightMatchers
        );
      });
    });
}

function normalizeText(value: string): string {
  return (value ?? "").replace(/\\n/g, "\n").replace(/\r/g, "").trim();
}

function splitReadableParagraphs(value: string): string[] {
  const normalized = normalizeText(value);
  if (!normalized) {
    return [];
  }

  return normalized
    .split(/\n+/)
    .flatMap((line) => line.split(PARAGRAPH_DELIMITER_REGEX))
    .flatMap((line) => line.split(NUMBERING_BREAK_REGEX))
    .map((line) => line.replace(/\s+/g, " ").trim())
    .filter(Boolean);
}

function sanitizeReasonText(value: string): string {
  const normalized = normalizeText(value);
  if (!normalized) {
    return "";
  }

  return normalized
    .split(/\n+/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .filter((line) => !line.startsWith("점수구성:"))
    .join("\n");
}

function formatSignedWon(value: number): string {
  if (value > 0) {
    return `+${value.toLocaleString()}원`;
  }
  if (value < 0) {
    return `${value.toLocaleString()}원`;
  }
  return "0원";
}

function ProductCard({
  rank,
  productType,
  title,
  summary,
  meta,
  reason,
  minExpectedMonthlyBenefit,
  expectedMonthlyBenefit,
  maxExpectedMonthlyBenefit,
  benefitComponents: _benefitComponents = [],
  detailFields = [],
  actionLabel,
  onAction,
  actionLoading,
  highlightKeywords,
  clickCount,
  clickRatePercent
}: ProductCardProps) {
  const [expanded, setExpanded] = useState(false);
  const [benefitHovered, setBenefitHovered] = useState(false);
  const [benefitTooltipVisible, setBenefitTooltipVisible] = useState(false);
  const benefitTooltipTimerRef = useRef<number | null>(null);

  const highlightMatchers = useMemo(() => buildHighlightMatchers(highlightKeywords), [highlightKeywords]);

  const normalizedDetails = useMemo(
    () => detailFields.filter((field) => field.label?.trim() && field.value?.trim()),
    [detailFields]
  );
  const hasDetails = normalizedDetails.length > 0;

  const summaryParagraphs = useMemo(() => splitReadableParagraphs(summary), [summary]);
  const metaParagraphs = useMemo(() => splitReadableParagraphs(meta), [meta]);
  const reasonParagraphs = useMemo(() => splitReadableParagraphs(sanitizeReasonText(reason)), [reason]);

  const clearBenefitTooltipTimer = () => {
    if (benefitTooltipTimerRef.current !== null) {
      window.clearTimeout(benefitTooltipTimerRef.current);
      benefitTooltipTimerRef.current = null;
    }
  };

  const handleBenefitHoverStart = () => {
    setBenefitHovered(true);
    clearBenefitTooltipTimer();
    benefitTooltipTimerRef.current = window.setTimeout(() => {
      setBenefitTooltipVisible(true);
    }, 500);
  };

  const handleBenefitHoverEnd = () => {
    clearBenefitTooltipTimer();
    setBenefitHovered(false);
    setBenefitTooltipVisible(false);
  };

  useEffect(() => {
    return () => {
      clearBenefitTooltipTimer();
    };
  }, []);

  const renderReadableBlock = (
    paragraphs: string[],
    className: string,
    keyPrefix: string
  ): ReactNode => {
    if (paragraphs.length === 0) {
      return null;
    }

    if (paragraphs.length === 1) {
      return (
        <p className={className}>
          {renderEmphasizedText(paragraphs[0], `${keyPrefix}-single`, highlightMatchers)}
        </p>
      );
    }

    return (
      <div className={`${className} readable-block`}>
        {paragraphs.map((paragraph, index) => (
          <p key={`${keyPrefix}-${index}`} className="readable-line">
            {renderEmphasizedText(paragraph, `${keyPrefix}-${index}`, highlightMatchers)}
          </p>
        ))}
      </div>
    );
  };

  const renderDetailValue = (field: RecommendationDetailField, index: number) => {
    const value = normalizeText(field.value);

    if (!READABLE_DETAIL_LABELS.has(field.label)) {
      return (
        <span className="detail-value-text">
          {renderEmphasizedText(value, `detail-${rank}-${index}`, highlightMatchers)}
        </span>
      );
    }

    const paragraphs = splitReadableParagraphs(value);
    if (paragraphs.length <= 1) {
      return (
        <span className="detail-value-text">
          {renderEmphasizedText(value, `detail-${rank}-${index}`, highlightMatchers)}
        </span>
      );
    }

    return (
      <div className="detail-readable-block">
        {paragraphs.map((paragraph, paragraphIndex) => (
          <p key={`detail-${rank}-${index}-${paragraphIndex}`} className="detail-readable-line">
            {renderEmphasizedText(
              paragraph,
              `detail-${rank}-${index}-${paragraphIndex}`,
              highlightMatchers
            )}
          </p>
        ))}
      </div>
    );
  };

  const benefitTitle = productType === "ACCOUNT" ? "계좌 기대 이득" : "카드 기대 이득";

  return (
    <li className="product-card">
      <div className="card-top">
        <p className="rank">TOP {rank}</p>
        <div
          className={`benefit-wrap${benefitHovered ? " is-hovered" : ""}`}
          onMouseEnter={handleBenefitHoverStart}
          onMouseLeave={handleBenefitHoverEnd}
        >
          <p className="benefit-value">{formatSignedWon(expectedMonthlyBenefit)}/월</p>
          <p className="benefit-range">
            범위 {minExpectedMonthlyBenefit.toLocaleString()}~{maxExpectedMonthlyBenefit.toLocaleString()}원/월
          </p>

          {benefitTooltipVisible ? (
            <div className="bundle-tooltip item-benefit-tooltip" role="tooltip">
              <p className="bundle-tooltip-title">월 예상 이득 근거</p>
              <div className="bundle-tooltip-summary">
                <p>
                  <span>{benefitTitle}</span>
                  <strong>{formatSignedWon(expectedMonthlyBenefit)}</strong>
                </p>
                <p>
                  <span>최소/최대</span>
                  <strong>
                    {minExpectedMonthlyBenefit.toLocaleString()}원 ~ {maxExpectedMonthlyBenefit.toLocaleString()}원
                  </strong>
                </p>
              </div>
            </div>
          ) : null}
        </div>
      </div>

      <h4>{title}</h4>
      {typeof clickCount === "number" && typeof clickRatePercent === "number" ? (
        <p className="click-metric">클릭 {clickCount}회 · 클릭률 {clickRatePercent.toFixed(1)}%</p>
      ) : null}

      {renderReadableBlock(summaryParagraphs, "summary", `summary-${rank}`)}
      {renderReadableBlock(metaParagraphs, "meta", `meta-${rank}`)}
      {renderReadableBlock(reasonParagraphs, "reason", `reason-${rank}`)}

      <div className="card-actions">
        {hasDetails ? (
          <button
            type="button"
            className="card-secondary-action"
            onClick={() => setExpanded((prev) => !prev)}
          >
            {expanded ? "상세 닫기" : "상세 보기"}
          </button>
        ) : null}

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
      </div>

      {expanded && hasDetails ? (
        <div className="card-details">
          <dl className="detail-grid">
            {normalizedDetails.map((field, index) => {
              const isLink = field.link || /^https?:\/\//i.test(field.value);
              return (
                <div key={`${field.label}-${index}`} className="detail-row">
                  <dt>{field.label}</dt>
                  <dd>
                    {isLink ? (
                      <a href={field.value} target="_blank" rel="noopener noreferrer">
                        {field.value}
                      </a>
                    ) : (
                      renderDetailValue(field, index)
                    )}
                  </dd>
                </div>
              );
            })}
          </dl>
        </div>
      ) : null}
    </li>
  );
}

export default ProductCard;
