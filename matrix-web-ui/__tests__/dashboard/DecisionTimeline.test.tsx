import { render, screen } from '@testing-library/react';
import DecisionTimeline from '@/components/dashboard/DecisionTimeline';
import type { ExplainStep } from '@/lib/matrix-client';

const SAMPLE_STEPS: ExplainStep[] = [
  { stage: 'INPUT_NORMALIZED', action: 'lowercase + trim', duration_ms: 1 },
  { stage: 'BIR_RULES_FIRED', action: 'rule-42, rule-87', duration_ms: 3 },
  { stage: 'HDC_MEMORY_RETRIEVED', action: 'kb-doc-42', duration_ms: 5 },
  { stage: 'MCTS_PLAN_SELECTED', action: 'plan-12', duration_ms: 4 },
  { stage: 'MODULATORS_APPLIED', action: 'ALL_PASSED', duration_ms: 2 },
];

describe('DecisionTimeline', () => {
  it('renders the heading', () => {
    render(<DecisionTimeline steps={SAMPLE_STEPS} totalDurationMs={15} />);
    expect(screen.getByText(/Decision Timeline/i)).toBeInTheDocument();
  });

  it('shows total duration', () => {
    render(<DecisionTimeline steps={SAMPLE_STEPS} totalDurationMs={15} />);
    expect(screen.getByText(/total: 15ms/)).toBeInTheDocument();
  });

  it('renders all step stages', () => {
    render(<DecisionTimeline steps={SAMPLE_STEPS} totalDurationMs={15} />);
    expect(screen.getByText(/INPUT_NORMALIZED/)).toBeInTheDocument();
    expect(screen.getByText(/BIR_RULES_FIRED/)).toBeInTheDocument();
    expect(screen.getByText(/HDC_MEMORY_RETRIEVED/)).toBeInTheDocument();
    expect(screen.getByText(/MCTS_PLAN_SELECTED/)).toBeInTheDocument();
    expect(screen.getByText(/MODULATORS_APPLIED/)).toBeInTheDocument();
  });

  it('renders legend with 5 stages', () => {
    render(<DecisionTimeline steps={SAMPLE_STEPS} totalDurationMs={15} />);
    expect(screen.getByText(/INPUT/i)).toBeInTheDocument();
    expect(screen.getByText(/BIR \(logic\)/i)).toBeInTheDocument();
    expect(screen.getByText(/HDC \(memory\)/i)).toBeInTheDocument();
    expect(screen.getByText(/MCTS \(plan\)/i)).toBeInTheDocument();
    expect(screen.getByText(/MODULATORS/i)).toBeInTheDocument();
  });

  it('handles empty steps', () => {
    render(<DecisionTimeline steps={[]} totalDurationMs={0} />);
    expect(screen.getByText(/total: 0ms/)).toBeInTheDocument();
  });
});
