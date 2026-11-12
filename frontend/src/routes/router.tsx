import { createBrowserRouter } from 'react-router'
import { AdminLayout } from '../layouts/AdminLayout'
import { AdminHomePage } from '../pages/admin/AdminHomePage'
import { CatalogPage } from '../pages/admin/catalog/CatalogPage'
import { CustomersPage } from '../pages/admin/CustomersPage'
import { ReportsPage } from '../pages/admin/ReportsPage'
import { ChangePasswordPage } from '../pages/admin/ChangePasswordPage'
import { ForgotPasswordPage } from '../pages/admin/ForgotPasswordPage'
import { OrderDetailPage } from '../pages/admin/orders/OrderDetailPage'
import { OrdersPage } from '../pages/admin/orders/OrdersPage'
import { LoginPage } from '../pages/admin/LoginPage'
import { NotificationsPage } from '../pages/admin/NotificationsPage'
import { ProductionPage } from '../pages/admin/ProductionPage'
import { FinancePage } from '../pages/admin/finance/FinancePage'
import { ResetPasswordPage } from '../pages/admin/ResetPasswordPage'
import { SchedulePage } from '../pages/admin/schedule/SchedulePage'
import { SettingsPage } from '../pages/admin/SettingsPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { HomePage } from '../pages/public/HomePage'
import { MenuPage } from '../pages/public/MenuPage'
import { OrderFormPage } from '../pages/public/order/OrderFormPage'
import { TrackingEditPage } from '../pages/public/tracking/TrackingEditPage'
import { TrackingPage } from '../pages/public/tracking/TrackingPage'
import { GuestOnly, RequireAuth } from './guards'

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '/cardapio', element: <MenuPage /> },
  { path: '/encomendar', element: <OrderFormPage /> },
  { path: '/acompanhar', element: <TrackingPage /> },
  { path: '/acompanhar/editar', element: <TrackingEditPage /> },
  {
    path: '/admin/login',
    element: (
      <GuestOnly>
        <LoginPage />
      </GuestOnly>
    ),
  },
  {
    path: '/admin/esqueci-senha',
    element: (
      <GuestOnly>
        <ForgotPasswordPage />
      </GuestOnly>
    ),
  },
  // Sem GuestOnly: o link do e-mail deve funcionar mesmo com uma sessão aberta em outro lugar.
  { path: '/admin/redefinir-senha', element: <ResetPasswordPage /> },
  {
    path: '/admin',
    element: (
      <RequireAuth>
        <AdminLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <AdminHomePage /> },
      { path: 'pedidos', element: <OrdersPage /> },
      { path: 'pedidos/:id', element: <OrderDetailPage /> },
      { path: 'producao', element: <ProductionPage /> },
      { path: 'agenda', element: <SchedulePage /> },
      { path: 'financeiro', element: <FinancePage /> },
      { path: 'catalogo', element: <CatalogPage /> },
      // Endereço antigo do catálogo, mantido para links salvos.
      { path: 'produtos', element: <CatalogPage /> },
      { path: 'clientes', element: <CustomersPage /> },
      { path: 'relatorios', element: <ReportsPage /> },
      { path: 'configuracoes', element: <SettingsPage /> },
      { path: 'avisos', element: <NotificationsPage /> },
      { path: 'trocar-senha', element: <ChangePasswordPage /> },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
