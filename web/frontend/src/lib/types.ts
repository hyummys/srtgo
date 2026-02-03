export type RailType = 'SRT' | 'KTX';

export interface TrainResult {
  train_name: string;
  train_number: string;
  dep_date: string;
  dep_time: string;
  dep_station: string;
  arr_time: string;
  arr_station: string;
  general_seat_state: string;
  special_seat_state: string;
  general_available: boolean;
  special_available: boolean;
  standby_available: boolean;
  duration_minutes: number;
  display: string;
}

export interface Passengers {
  adult: number;
  child: number;
  senior: number;
  disability1to3: number;
  disability4to6: number;
}

export type SeatType = 'GENERAL_FIRST' | 'GENERAL_ONLY' | 'SPECIAL_FIRST' | 'SPECIAL_ONLY';

export interface MacroTask {
  task_id: string;
  rail_type: RailType;
  departure: string;
  arrival: string;
  date: string;
  time: string;
  status: 'pending' | 'running' | 'success' | 'failed' | 'cancelled';
  attempts: number;
  elapsed: number;
}

export interface MacroEvent {
  type: 'tick' | 'success' | 'error' | 'relogin' | 'cancelled' | 'failed';
  attempts?: number;
  elapsed?: number;
  status?: string;
  reservation?: ReservationData;
  message?: string;
  error_type?: string;
}

export interface ReservationData {
  reservation_number: string;
  train_name: string;
  train_number: string;
  dep_date: string;
  dep_time: string;
  dep_station: string;
  arr_time: string;
  arr_station: string;
  total_cost: number;
  seat_count: number;
  paid: boolean;
  is_waiting: boolean;
  is_ticket: boolean;
  payment_date?: string;
  payment_time?: string;
  tickets: TicketData[];
  display: string;
}

export interface TicketData {
  car: string;
  seat: string;
  seat_type: string;
  passenger_type: string;
  price: number;
  original_price: number;
  discount: number;
  is_waiting: boolean;
}

export interface AuthStatus {
  SRT: { logged_in: boolean; id: string | null };
  KTX: { logged_in: boolean; id: string | null };
}

export interface StationSettings {
  available: string[];
  selected: string[];
}

export interface TelegramSettings {
  token: string;
  chat_id: string;
  enabled: boolean;
}

export interface CardSettings {
  number_masked: string;
  has_birthday: boolean;
  has_expire: boolean;
  enabled: boolean;
}

export interface DefaultSettings {
  departure: string;
  arrival: string;
  date: string;
  time: string;
  adult: number;
  child: number;
  senior: number;
  disability1to3: number;
  disability4to6: number;
}

// User authentication
export interface User {
  id: number;
  username: string;
  nickname: string;
  role: 'admin' | 'user';
  status?: 'pending' | 'approved' | 'rejected';
  created_at?: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}

export interface RegisterResponse {
  status: 'approved' | 'pending';
  message: string;
  token?: string;
  user?: User;
}

export interface AdminUser {
  id: number;
  username: string;
  nickname: string;
  role: string;
  status: string;
  created_at: string;
  updated_at: string;
}
