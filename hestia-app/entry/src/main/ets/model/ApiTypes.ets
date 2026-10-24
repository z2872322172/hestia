/**
 * 后端统一响应结构（common/api/Result.java）。
 *
 * code === 'OK' 表示成功；其余为业务错误码，错误文案直接取 message。
 */
export interface ApiResult<T> {
  code: string;
  message: string;
  data: T;
  traceId: string;
}

/** 后端统一分页结构（common/api/PageResult.java）。page 从 1 开始。 */
export interface PageResult<T> {
  records: T[];
  page: number;
  size: number;
  total: number;
}

/** 空分页兜底，避免页面里到处判空。 */
export function emptyPage<T>(): PageResult<T> {
  const result: PageResult<T> = {
    records: [],
    page: 1,
    size: 10,
    total: 0
  };
  return result;
}
