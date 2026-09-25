import { render, screen } from '@testing-library/react';
import Pricing from '@/components/sections/Pricing';

describe('Pricing section', () => {
  it('renders the section heading', () => {
    render(<Pricing />);
    expect(screen.getByText(/Start free. Scale when ready./i)).toBeInTheDocument();
  });

  it('displays all three plans', () => {
    render(<Pricing />);
    expect(screen.getByText(/FREE/i)).toBeInTheDocument();
    expect(screen.getByText(/PRO/i)).toBeInTheDocument();
    expect(screen.getByText(/ENTERPRISE/i)).toBeInTheDocument();
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
    expect(screen.getByText(/Contact Sales/i)).toBeInTheDocument();
  });

  it('shows enterprise on-prem option', () => {
    render(<Pricing />);
    expect(screen.getByText(/On-prem deployment/i)).toBeInTheDocument();
  });
});
