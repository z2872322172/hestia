/** 单个维度得分（ReviewView.DimensionScoreView）。 */
export interface DimensionScore {
  dimensionCode: string;
  score: number;
}

/** 评价（ReviewView.java）。 */
export interface Review {
  id: number;
  reviewNo: string;
  bookingId: number;
  customerId: number;
  workerId: number;
  overallScore: number;
  content: string;
  status: string;
  hiddenReason: string;
  createdAt: string;
  dimensions: DimensionScore[];
  fileIds: number[];
}

/** 评价维度元数据。 */
export interface DimensionMeta {
  code: string;
  name: string;
}

/**
 * 评价维度。
 *
 * 后端目前没有暴露维度查询接口（ReviewDimensionMapper.selectActive 只在
 * ReviewApplicationService.submit 内部使用），所以这里先硬编码，
 * 与 V6 迁移里的种子保持一致：SERVICE/ATTITUDE/QUALITY/PUNCTUALITY，权重各 0.25。
 *
 * TODO: 后端补 GET /customer/review-dimensions 后改为接口读取，本常量仅作兜底。
 */
export const REVIEW_DIMENSIONS: DimensionMeta[] = [
  { code: 'SERVICE', name: '服务效果' },
  { code: 'ATTITUDE', name: '服务态度' },
  { code: 'QUALITY', name: '服务质量' },
  { code: 'PUNCTUALITY', name: '准时上门' }
];

/**
 * 维度得分表。
 *
 * 刻意用固定字段而不是 Map/Record：
 * 1. JSON.stringify 对 Map 会输出 {}，提交后后端收到空对象必然 400；
 * 2. ArkTS 严格模式禁止动态索引赋值，拼不出 { [code]: score } 这种结构。
 * 后端是 Map<String, BigDecimal>，正好接收这 4 个键。
 */
export interface DimensionScores {
  SERVICE: number;
  ATTITUDE: number;
  QUALITY: number;
  PUNCTUALITY: number;
}

/** 提交评价请求体（CustomerReviewController.SubmitReviewRequest）。 */
export interface SubmitReviewBody {
  dimensionScores: DimensionScores;
  content: string;
  fileIds: number[];
}

/** 新建空的维度得分表（默认 5 分）。 */
export function newDimensionScores(): DimensionScores {
  const scores: DimensionScores = {
    SERVICE: 5,
    ATTITUDE: 5,
    QUALITY: 5,
    PUNCTUALITY: 5
  };
  return scores;
}

/** 按维度编码读分。 */
export function scoreOf(scores: DimensionScores, code: string): number {
  if (code === 'SERVICE') {
    return scores.SERVICE;
  }
  if (code === 'ATTITUDE') {
    return scores.ATTITUDE;
  }
  if (code === 'QUALITY') {
    return scores.QUALITY;
  }
  if (code === 'PUNCTUALITY') {
    return scores.PUNCTUALITY;
  }
  return 0;
}

/** 按维度编码写分，返回新对象（避免 @State 引用未变化导致不刷新）。 */
export function withScore(scores: DimensionScores, code: string, value: number): DimensionScores {
  const next: DimensionScores = {
    SERVICE: scores.SERVICE,
    ATTITUDE: scores.ATTITUDE,
    QUALITY: scores.QUALITY,
    PUNCTUALITY: scores.PUNCTUALITY
  };
  if (code === 'SERVICE') {
    next.SERVICE = value;
  } else if (code === 'ATTITUDE') {
    next.ATTITUDE = value;
  } else if (code === 'QUALITY') {
    next.QUALITY = value;
  } else if (code === 'PUNCTUALITY') {
    next.PUNCTUALITY = value;
  }
  return next;
}

/** 4 维度平均分，四舍五入到 0.5（与后端权重 0.25 一致）。 */
export function averageScore(scores: DimensionScores): number {
  const sum: number = scores.SERVICE + scores.ATTITUDE + scores.QUALITY + scores.PUNCTUALITY;
  return Math.round((sum / 4) * 2) / 2;
}
