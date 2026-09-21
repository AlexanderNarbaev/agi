import { render, screen } from '@testing-library/react';
import ConfidenceScore from '@/components/dashboard/ConfidenceScore';
import type { ConfidenceBreakdown } from '@/lib/matrix-client';

const SAMPLE: ConfidenceBreakdown = {
  bir_confidence: 0.85,
  hdc_confidence: 0.78,
  mcts_confidence: 0.72,
  aggregate: 0.95,
};

describe('ConfidenceScore', () => {
  it('renders the heading', () => {
    render(<ConfidenceScore breakdown={SAMPLE} />);
    expect(screen.getByText(/Confidence Score/i)).toBeInTheDocument();
  });

  it('shows aggregate as percentage', () => {
    render(<ConfidenceScore breakdown={SAMPLE} />);
    expect(screen.getByText('95', { exact: false })).toBeInTheDocument();
    expect(screen.getByText(/%/)).toBeInTheDocument();
  });

  it('shows each stage with value', () => {
    render(<ConfidenceScore breakdown={SAMPLE} />);
    expect(screen.getByText(/BIR \(logic\)/)).toBeInTheDocument();
    expect(screen.getByText(/HDC \(memory\)/)).toBeInTheDocument();
    expect(screen.getByText(/MCTS \(planning\)/)).toBeInTheDocument();
  });

  it('displays aggregate rule explanation', () => {
    render(<ConfidenceScore breakdown={SAMPLE} />);
    expect(screen.getByText(/Aggregate rule/i)).toBeInTheDocument();
  });

  it('handles low aggregate', () => {
    render(<ConfidenceScore breakdown={{
      bir_confidence: 0.5,
      hdc_confidence: 0.4,
      mcts_confidence: 0.3,
      aggregate: 0.2,
    }} />);
    expect(screen.getByText('20', { exact: false })).toBeInTheDocument();
  });
});
