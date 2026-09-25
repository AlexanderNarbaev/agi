import { render, screen } from '@testing-library/react';
import ModulatorState from '@/components/dashboard/ModulatorState';
import type { ModulatorSnapshot } from '@/lib/matrix-client';

const SAMPLE: ModulatorSnapshot = {
  ethical_filter: 0.95,
  safety_monitor: 0.92,
  consistency_checker: 0.88,
  lie_detector: 0.91,
};

describe('ModulatorState', () => {
  it('renders the heading', () => {
    render(<ModulatorState snapshot={SAMPLE} />);
    expect(screen.getByText(/Modulator State/i)).toBeInTheDocument();
  });

  it('displays all 4 modulators', () => {
    render(<ModulatorState snapshot={SAMPLE} />);
    expect(screen.getByText(/ETHICAL_FILTER/)).toBeInTheDocument();
    expect(screen.getByText(/SAFETY_MONITOR/)).toBeInTheDocument();
    expect(screen.getByText(/CONSISTENCY_CHECKER/)).toBeInTheDocument();
    expect(screen.getByText(/LIE_DETECTOR/)).toBeInTheDocument();
  });

  it('shows average across all modulators', () => {
    render(<ModulatorState snapshot={SAMPLE} />);
    // average = (0.95 + 0.92 + 0.88 + 0.91) / 4 = 0.915
    expect(screen.getByText(/avg: 0.92/)).toBeInTheDocument();
  });

  it('shows individual values', () => {
    render(<ModulatorState snapshot={SAMPLE} />);
    expect(screen.getByText('0.95')).toBeInTheDocument();
    expect(screen.getByText('0.92')).toBeInTheDocument();
    expect(screen.getByText('0.88')).toBeInTheDocument();
    expect(screen.getByText('0.91')).toBeInTheDocument();
  });

  it('marks all modulators as FROZEN per CONSTITUTION IV', () => {
    render(<ModulatorState snapshot={SAMPLE} />);
    expect(screen.getByText(/CONSTITUTION IV/i)).toBeInTheDocument();
    expect(screen.getByText(/FROZEN/)).toBeInTheDocument();
  });

  it('displays descriptions', () => {
    render(<ModulatorState snapshot={SAMPLE} />);
    expect(screen.getByText(/Refuses outputs violating ethical guidelines/i)).toBeInTheDocument();
  });
});
