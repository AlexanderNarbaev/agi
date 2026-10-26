import { render, screen, within } from '@testing-library/react';
import Pricing from '@/components/sections/Pricing';

describe('Pricing section', () => {
  it('renders the section heading', () => {
    render(<Pricing />);
    expect(screen.getByText(/Start free. Scale when ready./i)).toBeInTheDocument();
  });

  it('displays all three plans', () => {
    render(<Pricing />);
    // RECON-W38.4: the plan name is the heading of the plan card. A bare
    // getByText(/FREE/i) matched several elements ("FREE" and the "Start Free
    // Trial" call to action) and the test failed with "Found multiple
    // elements"; the heading is the element the assertion actually means.
    expect(screen.getByRole('heading', { name: 'FREE' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'PRO' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'ENTERPRISE' })).toBeInTheDocument();
  });

  it('shows PRO plan as most popular', () => {
    render(<Pricing />);
    expect(screen.getByText(/Most Popular/i)).toBeInTheDocument();
  });

  it('displays free plan rate limit', () => {
    render(<Pricing />);
    expect(screen.getByText(/100 API requests \/ hour/i)).toBeInTheDocument();
  });

  it('displays enterprise contact sales option', () => {
    render(<Pricing />);
    // RECON-W38.4: "Contact Sales" is the enterprise plan's call to action, but
    // the section also ends with a "Need something custom? Contact Sales"
    // link, so a page-wide query found two elements. The query is scoped to
    // the enterprise plan card.
    const enterpriseCard = screen.getByRole('heading', { name: 'ENTERPRISE' }).closest('div');
    expect(enterpriseCard).not.toBeNull();
    expect(within(enterpriseCard as HTMLElement).getByRole('link', { name: /Contact Sales/i }))
      .toBeInTheDocument();
  });

  it('shows enterprise on-prem option', () => {
    render(<Pricing />);
    expect(screen.getByText(/On-prem deployment/i)).toBeInTheDocument();
  });
});
