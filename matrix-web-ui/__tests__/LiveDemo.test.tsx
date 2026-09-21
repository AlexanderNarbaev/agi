import { render, screen, fireEvent } from '@testing-library/react';
import LiveDemo from '@/components/sections/LiveDemo';

describe('LiveDemo section', () => {
  it('renders the section heading', () => {
    render(<LiveDemo />);
    expect(screen.getByText(/Live Demo/i)).toBeInTheDocument();
  });

  it('has an input field and analyze button', () => {
    render(<LiveDemo />);
    expect(screen.getByLabelText(/your query/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /analyze/i })).toBeInTheDocument();
  });

  it('renders without crashing', () => {
    const { container } = render(<LiveDemo />);
    expect(container.firstChild).toBeInTheDocument();
  });

  it('button is disabled when input is empty', () => {
    render(<LiveDemo />);
    const button = screen.getByRole('button', { name: /analyze/i });
    expect(button).toBeDisabled();
  });

  it('enables button when user types', () => {
    render(<LiveDemo />);
    const input = screen.getByLabelText(/your query/i);
    fireEvent.change(input, { target: { value: 'hello' } });
    const button = screen.getByRole('button', { name: /analyze/i });
    expect(button).not.toBeDisabled();
  });

  it('provides clickable suggestion examples', () => {
    render(<LiveDemo />);
    expect(screen.getByText(/capital of france/i)).toBeInTheDocument();
    expect(screen.getByText(/2\+2/i)).toBeInTheDocument();
  });
});
