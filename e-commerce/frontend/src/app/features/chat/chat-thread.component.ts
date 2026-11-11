import { DatePipe } from '@angular/common';
import { Component, ElementRef, ViewChild, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatConversation, ChatMessage, ChatUser } from '../../core/models';
import { TranslatePipe } from '../../core/translate.pipe';

@Component({
  selector: 'app-chat-thread',
  imports: [DatePipe, FormsModule, TranslatePipe],
  templateUrl: './chat-thread.component.html',
})
export class ChatThreadComponent {
  readonly conversation = input<ChatConversation | null>(null);
  readonly messages = input<ChatMessage[]>([]);
  readonly me = input<ChatUser | null>(null);
  readonly typing = input<string | null>(null);
  readonly staff = input(false);
  readonly hasMore = input(false);
  readonly send = output<string>();
  readonly loadOlder = output<void>();
  readonly assignToMe = output<void>();
  readonly closeConversation = output<void>();
  readonly typingStart = output<void>();

  @ViewChild('messageList') private messageList?: ElementRef<HTMLElement>;
  readonly body = signal('');

  constructor() {
    effect(() => {
      this.messages();
      if (typeof window !== 'undefined') {
        setTimeout(() => {
          const element = this.messageList?.nativeElement;
          if (element) element.scrollTop = element.scrollHeight;
        }, 0);
      }
    });
  }

  submit(): void {
    const body = this.body().trim();
    if (!body) return;
    this.send.emit(body);
    this.body.set('');
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.submit();
    }
  }

  onInput(): void {
    this.typingStart.emit();
  }

  isMine(message: ChatMessage): boolean {
    return this.me()?.id === message.sender.id;
  }

  isLast(message: ChatMessage): boolean {
    const all = this.messages();
    return all.length > 0 && all[all.length - 1].id === message.id;
  }

  canClose(): boolean {
    return this.staff() && this.conversation()?.type === 'SUPPORT' && this.conversation()?.status === 'OPEN';
  }

  canAssign(): boolean {
    return this.staff() && this.conversation()?.type === 'SUPPORT' && !this.conversation()?.assignedToMe;
  }
}
