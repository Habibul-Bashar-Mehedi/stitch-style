export interface Category {
  id: number;
  parentId: number | null;
  name: string;
  slug: string;
  description?: string;
  imageUrl?: string;
  imageMediaId?: number | null;
  sizeType: string;
  displayOrder: number;
  depth: number;
  active: boolean;
  showOnHome: boolean;
  showInNavbar: boolean;
  children: Category[];
}

export interface Variant {
  id: number;
  sizeLabel: string;
  sku: string;
  stockQuantity: number;
  lowStockThreshold: number;
  reservedQuantity: number;
  available: number;
  priceOverride?: number;
  active: boolean;
}

export interface ProductImage {
  id: number;
  mediaId: number;
  url: string;
  sortOrder: number;
  primary: boolean;
}

export interface Product {
  id: number;
  categoryId: number;
  categorySlug: string;
  nameI18n: Record<string, string>;
  slug: string;
  descriptionI18n: Record<string, string>;
  name: string;
  description: string;
  basePrice: number;
  discountPrice?: number;
  effectivePrice: number;
  sizeType: string;
  originCountry?: string;
  brand?: string;
  model?: string;
  deliveryEstimate?: string;
  active: boolean;
  featured: boolean;
  variants: Variant[];
  images: ProductImage[];
  createdAt: string;
}

export interface Banner {
  id: number;
  title: string;
  imageUrl: string;
  linkUrl?: string;
  displayOrder: number;
  startsAt?: string;
  endsAt?: string;
}

export interface Notice {
  id: number;
  title: string;
  body: string;
  priority: number;
  startsAt?: string;
  endsAt?: string;
}

export interface Home {
  settings: Record<string, string>;
  banners: Banner[];
  notices: Notice[];
  cards: Category[];
  latestProducts: Product[];
}

export interface DeliveryArea {
  id: number;
  name: string;
  district?: string;
  deliveryCharge: number;
  freeShippingThreshold?: number;
  slaDays?: number;
  displayOrder: number;
}

export interface PaymentMethod {
  code: string;
  label: string;
  enabled: boolean;
}

export interface CartInput {
  variantId: number;
  quantity: number;
  selected: boolean;
}

export interface CartLine {
  variantId: number;
  productId: number;
  productSlug: string;
  productName: string;
  sizeLabel: string;
  quantity: number;
  selected: boolean;
  unitPrice: number;
  lineTotal: number;
  available: number;
  priceChanged: boolean;
  stockChanged: boolean;
}

export interface Cart {
  lines: CartLine[];
  subtotal: number;
  deliveryCharge: number;
  discountTotal: number;
  grandTotal: number;
}

export interface OrderResponse {
  orderNumber: string;
  publicToken: string;
  status: string;
  paymentStatus: string;
  currency: string;
  subtotal: number;
  deliveryCharge: number;
  discountTotal: number;
  grandTotal: number;
  trackingUrl: string;
  redirectUrl?: string;
  replayed?: boolean;
}

export interface TrackingItem {
  orderItemId: number;
  productName: string;
  sizeLabel: string;
  quantity: number;
  unitPrice: number;
}

export interface Tracking {
  orderNumber: string;
  status: string;
  paymentStatus: string;
  grandTotal: number;
  placedAt: string;
  shippedAt?: string;
  deliveredAt?: string;
  items: TrackingItem[];
}

export type ReturnEligibility = 'ELIGIBLE' | 'NOT_DELIVERED' | 'WINDOW_CLOSED' | 'ALREADY_REQUESTED';

export interface ReturnQuote {
  itemsAmount: number;
  couponDiscountShare: number;
  deliveryAmount: number;
  total: number;
  fullOrder: boolean;
  currency: string;
  windowEndsAt?: string | null;
}

export interface ReturnSummary {
  returnId?: number | null;
  status?: string | null;
  requestedAt?: string | null;
  resolvedAt?: string | null;
  windowEndsAt?: string | null;
  eligibility: ReturnEligibility;
  ineligibleReason?: string | null;
  previousReturnId?: number | null;
  previousRejectionReason?: string | null;
}

export interface MyOrder {
  orderNumber: string;
  status: string;
  paymentStatus: string;
  grandTotal: number;
  placedAt: string;
  shippedAt?: string;
  deliveredAt?: string;
  items: TrackingItem[];
  returnInfo: ReturnSummary;
}

export interface Me {
  id: number;
  fullName: string;
  email: string;
  pictureUrl?: string;
  phone?: string;
  role: 'ADMIN' | 'MANAGER' | 'CUSTOMER';
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CheckoutPayload {
  customer: { name: string; phone: string };
  shippingAddress: string;
  deliveryAreaId: number;
  paymentMethod: 'COD' | 'BKASH' | 'SSLCOMMERZ';
  note?: string;
  couponCode?: string;
  items: { variantId: number; quantity: number; selected: boolean }[];
}

export interface Staff {
  id: number;
  fullName: string;
  email: string;
  role: 'ADMIN' | 'MANAGER' | 'CUSTOMER';
  active: boolean;
  lastLoginAt?: string;
}

export interface AuditLog {
  id: number;
  actorEmail?: string;
  action: string;
  entityType: string;
  entityId?: number;
  beforeState?: Record<string, unknown>;
  afterState?: Record<string, unknown>;
  ipAddress?: string;
  createdAt: string;
}

export interface ReturnRecord {
  id: number;
  orderNumber: string;
  status: string;
  reason?: string;
  resolutionNote?: string;
  refundMethod?: string;
  refundAmount?: number;
  requestedAt: string;
  resolvedAt?: string;
  quote?: ReturnQuote;
  items: { id: number; orderItemId: number; productName: string; sizeLabel: string; quantity: number; condition: string; unitPrice?: number; lineTotal?: number }[];
}

export interface Complaint {
  id: number;
  name: string;
  phone: string;
  orderNumber?: string;
  message: string;
  status: string;
  dueAt: string;
  respondedAt?: string;
  response?: string;
  createdAt: string;
  overdue: boolean;
}

export interface Coupon {
  id: number;
  code: string;
  discountType: string;
  discountValue: number;
  minSubtotal?: number;
  maxUses?: number;
  usedCount: number;
  startsAt?: string;
  endsAt?: string;
  active: boolean;
}

export interface Review {
  id: number;
  productId: number;
  productName: string;
  userDisplayName: string;
  rating: number;
  comment?: string;
  status: string;
  verifiedPurchase: boolean;
  createdAt: string;
}

export interface Analytics {
  from: string;
  to: string;
  orders: number;
  revenue?: number;
  averageOrderValue?: number;
  topProducts: { productName: string; quantity: number; revenue: number }[];
  statusDistribution: { status: string; count: number }[];
  sla: { shippedWithin48Hours: number; deliveredWithinSla: number; openComplaints: number; overdueComplaints: number };
}

export interface Invoice {
  orderNumber: string;
  customerName: string;
  customerPhone: string;
  shippingAddress: string;
  deliveryArea: string;
  paymentMethod: string;
  paymentStatus: string;
  status: string;
  placedAt: string;
  items: { productName: string; sizeLabel: string; sku: string; quantity: number; unitPrice: number; lineTotal: number }[];
  subtotal: number;
  deliveryCharge: number;
  discountTotal: number;
  couponDiscount: number;
  grandTotal: number;
  businessName: string;
  businessPhone: string;
}

export interface AdminOrder {
  id: number;
  orderNumber: string;
  customerName: string;
  customerPhone: string;
  status: string;
  paymentStatus: string;
  grandTotal: number;
  placedAt: string;
}

export type ChatConversationType = 'SUPPORT' | 'INTERNAL';
export type ChatConversationStatus = 'OPEN' | 'CLOSED';

export interface ChatUser {
  id: number;
  fullName: string;
  role: 'ADMIN' | 'MANAGER' | 'CUSTOMER';
  pictureUrl?: string;
}

export interface ChatConversation {
  id: number;
  type: ChatConversationType;
  status: ChatConversationStatus;
  counterpart?: ChatUser | null;
  assignedStaff?: ChatUser | null;
  orderNumber?: string | null;
  lastMessagePreview?: string | null;
  lastMessageAt?: string | null;
  unreadCount: number;
  assignedToMe: boolean;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  sender: ChatUser;
  body: string;
  clientMessageId: string;
  createdAt: string;
  readByOthers: boolean;
}

export interface ChatMessagePage {
  items: ChatMessage[];
  nextBeforeId?: number | null;
  hasMore: boolean;
}

export interface ChatContact {
  id: number;
  fullName: string;
  role: 'ADMIN' | 'MANAGER';
  pictureUrl?: string;
}

export interface ChatEvent {
  eventId: string;
  type: 'MESSAGE_CREATED' | 'MESSAGE_READ' | 'TYPING' | 'CONVERSATION_UPDATED';
  conversationId: number;
  message?: ChatMessage | null;
  readerId?: number | null;
  lastReadMessageId?: number | null;
  change?: string | null;
  typingUser?: string | null;
}
