export const STATIONS: Record<string, string[]> = {
  SRT: [
    '수서', '동탄', '평택지제', '경주', '곡성', '공주', '광주송정', '구례구',
    '김천(구미)', '나주', '남원', '대전', '동대구', '마산', '목포', '밀양',
    '부산', '서대구', '순천', '여수EXPO', '여천', '오송', '울산(통도사)',
    '익산', '전주', '정읍', '진영', '진주', '창원', '창원중앙', '천안아산', '포항',
  ],
  KTX: [
    '서울', '용산', '영등포', '광명', '수원', '천안아산', '오송', '대전',
    '서대전', '김천구미', '동대구', '경주', '포항', '밀양', '구포', '부산',
    '울산(통도사)', '마산', '창원중앙', '경산', '논산', '익산', '정읍',
    '광주송정', '목포', '전주', '순천', '여수EXPO', '청량리', '강릉',
    '행신', '정동진', '진영',
  ],
};

export const DEFAULT_STATIONS: Record<string, string[]> = {
  SRT: ['수서', '대전', '동대구', '부산'],
  KTX: ['서울', '대전', '동대구', '부산'],
};

export const SEAT_TYPES = [
  { value: 'GENERAL_FIRST', label: '일반실 우선' },
  { value: 'GENERAL_ONLY', label: '일반실만' },
  { value: 'SPECIAL_FIRST', label: '특실 우선' },
  { value: 'SPECIAL_ONLY', label: '특실만' },
] as const;

export const PASSENGER_TYPES = [
  { key: 'adult', label: '어른' },
  { key: 'child', label: '어린이' },
  { key: 'senior', label: '경로' },
  { key: 'disability1to3', label: '장애 1~3급' },
  { key: 'disability4to6', label: '장애 4~6급' },
] as const;

export function formatTime(hhmmss: string): string {
  if (!hhmmss || hhmmss.length < 4) return hhmmss;
  return `${hhmmss.slice(0, 2)}:${hhmmss.slice(2, 4)}`;
}

export function formatDate(yyyymmdd: string): string {
  if (!yyyymmdd || yyyymmdd.length < 8) return yyyymmdd;
  return `${yyyymmdd.slice(4, 6)}/${yyyymmdd.slice(6, 8)}`;
}

export function formatElapsed(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
