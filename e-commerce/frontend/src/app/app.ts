import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { ApiService } from './core/api.service';
import { AuthService } from './core/auth.service';
import { CartService } from './core/cart.service';
import { ChatUnreadService } from './core/chat-unread.service';
import { WishlistService } from './core/wishlist.service';
import { I18nService } from './core/i18n.service';
import { TranslatePipe } from './core/translate.pipe';
import { Category, Notice } from './core/models';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, TranslatePipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly cart = inject(CartService);
  readonly chatUnread = inject(ChatUnreadService);
  readonly wishlist = inject(WishlistService);
  readonly i18n = inject(I18nService);

  readonly brandName = signal('Stitch & Style');
  readonly navbar = signal<Category[]>([]);
  readonly notices = signal<Notice[]>([]);
  readonly dismissed = signal<Set<number>>(new Set());
  readonly visibleNotices = computed(() => this.notices().filter((notice) => !this.dismissed().has(notice.id)));

  ngOnInit(): void {
    this.api.getCsrf().subscribe({ error: () => {} });
    this.api.getHome().subscribe({
      next: (home) => {
        this.brandName.set(home.settings['brand.name'] ?? 'Stitch & Style');
        this.notices.set(home.notices);
      },
      error: () => {},
    });
    this.api.getCategories().subscribe({
      next: (categories) => this.navbar.set(categories.filter((category) => category.showInNavbar)),
      error: () => {},
    });
    this.auth.load().subscribe(() => {
      this.cart.initialize();
      this.refreshChatUnread();
    });
  }

  private refreshChatUnread(): void {
    if (this.auth.me()?.role === 'CUSTOMER') this.chatUnread.refresh();
  }

  dismiss(noticeId: number): void {
    const next = new Set(this.dismissed());
    next.add(noticeId);
    this.dismissed.set(next);
  }

  logout(): void {
    this.auth.logout();
    this.cart.cart.set({ lines: [], subtotal: 0, deliveryCharge: 0, discountTotal: 0, grandTotal: 0 });
    this.chatUnread.set(0);
    this.router.navigateByUrl('/');
  }
}
