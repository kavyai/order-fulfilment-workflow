import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  template: `
    <div class="container">
      <header>
        <h1>Order Fulfilment Dashboard</h1>
        <p>Manage orders, credit reviews and notifications</p>
      </header>

      <section class="card">
        <h2>Create Order</h2>

        <form (ngSubmit)="createOrder()">
          <label>Order ID</label>
          <input
            name="orderId"
            [(ngModel)]="form.orderId"
            required
            placeholder="ORD-1001"
          />

          <label>Customer ID</label>
          <input
            name="customerId"
            [(ngModel)]="form.customerId"
            required
            placeholder="CUST-101"
          />

          <label>Amount (EUR)</label>
          <input
            name="amount"
            type="number"
            [(ngModel)]="form.amount"
            required
            min="0.01"
          />

          <label>Notification Preference</label>
          <select name="notificationPreference"
                  [(ngModel)]="form.notificationPreference">
            <option value="EMAIL">Email</option>
            <option value="SMS">SMS</option>
            <option value="BOTH">Email and SMS</option>
            <option value="NONE">None</option>
          </select>

          <button type="submit" [disabled]="loading">
            {{ loading ? 'Submitting...' : 'Submit Order' }}
          </button>
        </form>

        <p class="message" *ngIf="message">{{ message }}</p>
        <p class="error" *ngIf="error">{{ error }}</p>
      </section>

      <section class="card">
        <div class="section-header">
          <h2>Orders</h2>
          <button type="button" class="secondary"
                  (click)="loadOrders()">Refresh</button>
        </div>

        <p *ngIf="orders.length === 0">No orders loaded yet.</p>

        <div class="table-wrapper" *ngIf="orders.length > 0">
          <table>
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Customer</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Rejection Reason</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let order of orders">
                <td>{{ order.orderId }}</td>
                <td>{{ order.customerId }}</td>
                <td>{{ order.amount }}</td>
                <td><span class="status">{{ order.status }}</span></td>
                <td>{{ order.rejectionReason || '-' }}</td>
                <td>
                  <button type="button" class="secondary"
                          (click)="sendPriority(order)"
                        [disabled]="!['PROCESS_STARTED', 'VALIDATED', 'PENDING_OVERRIDE'].includes(order.status)">
                    Update Priority
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="card">
        <div class="section-header">
          <h2>Pending Credit Reviews</h2>
          <button type="button" class="secondary"
                  (click)="loadReviews()">Refresh</button>
        </div>

        <div *ngIf="reviews.length === 0">
          No pending credit reviews loaded.
        </div>

        <div class="review" *ngFor="let task of reviews">
          <p><strong>{{ task.name }}</strong></p>
          <p>Task ID: {{ task.id }}</p>
          <input
            [name]="'comment-' + task.id"
            [(ngModel)]="comments[task.id]"
            placeholder="Enter credit review comment"
          />
          <div class="actions">
            <button type="button" (click)="completeReview(task, true)">
              Approve
            </button>
            <button type="button" class="danger"
                    (click)="completeReview(task, false)">
              Reject
            </button>
          </div>
        </div>
      </section>
    </div>
  `,
  styleUrls:['./app.component.css']
})
export class AppComponent implements OnInit {
  private readonly api = 'http://localhost:8081/api/orders';

  form = {
    orderId: '',
    customerId: 'CUST-101',
    amount: 500,
    notificationPreference: 'BOTH'
  };

  orders: any[] = [];
  reviews: any[] = [];
  comments: Record<string, string> = {};
  loading = false;
  message = '';
  error = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadOrders();
    this.loadReviews();
  }

  createOrder(): void {
    this.loading = true;
    this.message = '';
    this.error = '';

    this.http.post(this.api, this.form).subscribe({
      next: (response: any) => {
        this.message = 'Order submitted: ' + response.orderId;
        this.form.orderId = '';
        this.loading = false;
        this.loadOrders();
        this.loadReviews();
      },
      error: (err) => {
        this.error = err.error?.detail ||
          err.error?.message || 'Could not create order.';
        this.loading = false;
      }
    });
  }

  loadOrders(): void {
    this.http.get<any[]>(this.api).subscribe({
      next: (data) => this.orders = data,
      error: () => this.error =
        'Could not load orders. Check whether the backend is running.'
    });
  }

  loadReviews(): void {
    this.http.get<any[]>(
      this.api + '/pending-credit-reviews'
    ).subscribe({
      next: (data) => this.reviews = data,
      error: () => this.error = 'Could not load credit reviews.'
    });
  }

  completeReview(task: any, approved: boolean): void {
    const comment = (this.comments[task.id] || '').trim();

    if (!comment) {
      this.error = 'Enter a comment before submitting a credit decision.';
      return;
    }

    this.http.post(
      this.api + '/credit-reviews/' + task.id,
      { approved, comment }
    ).subscribe({
      next: () => {
        this.message = approved
          ? 'Credit approved.'
          : 'Credit rejected.';
        this.error = '';
        this.loadOrders();
        this.loadReviews();
      },
      error: (err) => this.error =
        err.error?.detail || 'Could not complete credit review.'
    });
  }

  sendPriority(order: any): void {
    this.http.post(
      this.api + '/' + order.orderId + '/priority',
      { priorityUpdateNote: 'Customer requested priority update' }
    ).subscribe({
      next: () => {
        this.message = 'Priority update sent for ' + order.orderId;
        this.error = '';
      },
      error: (err) => this.error =
        err.error?.detail || 'Could not send priority update.'
    });
  }
}
