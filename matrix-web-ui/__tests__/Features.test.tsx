import { render, screen } from '@testing-library/react';
import Features from '@/components/sections/Features';

describe('Features section', () => {
  it('renders the section heading', () => {
    render(<Features />);
    expect(screen.getByText(/Why MATRIX/i)).toBeInTheDocument();
  });

  it('renders six feature cards', () => {
    render(<Features />);
    expect(screen.getByText(/Three Engines/i)).toBeInTheDocument();
    expect(screen.getByText(/XAI Native/i)).toBeInTheDocument();
    expect(screen.getByText(/No LLM in Runtime/i)).toBeInTheDocument();
    expect(screen.getByText(/GDPR Compliant/i)).toBeInTheDocument();
    expect(screen.getByText(/FROZEN Modulators/i)).toBeInTheDocument();
    expect(screen.getByText(/Liquid Federation/i)).toBeInTheDocument();
  });

  it('includes comparison table with MATRIX vs Pure LLM', () => {
    render(<Features />);
    expect(screen.getByText(/MATRIX vs Pure LLM/i)).toBeInTheDocument();
  });

  it('mentions CONSTITUTION-enforced modulators', () => {
    render(<Features />);
    expect(screen.getByText(/Constitution-enforced/i)).toBeInTheDocument();
  });
});
