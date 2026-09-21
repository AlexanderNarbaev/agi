import { render, screen } from '@testing-library/react';
import VectorMap from '@/components/dashboard/VectorMap';

// Mock the dynamic Three.js canvas to avoid jsdom issues with WebGL
jest.mock('@/components/dashboard/HDCDashboardCanvas', () => {
  return function MockHDCDashboardCanvas() {
    return <div data-testid="mock-hdc-canvas">HDC Dashboard Canvas</div>;
  };
});

describe('VectorMap', () => {
  it('renders the heading', () => {
    render(<VectorMap memoryHits={['kb-doc-42']} />);
    expect(screen.getByText(/HDC Vector Map/i)).toBeInTheDocument();
  });

  it('shows hit count', () => {
    render(<VectorMap memoryHits={['kb-doc-1', 'kb-doc-2', 'kb-doc-3']} />);
    expect(screen.getByText(/3 hits/)).toBeInTheDocument();
  });

  it('renders HDC canvas', () => {
    render(<VectorMap memoryHits={['kb-doc-42']} />);
    expect(screen.getByTestId('mock-hdc-canvas')).toBeInTheDocument();
  });

  it('lists retrieved memory hits', () => {
    render(<VectorMap memoryHits={['kb-doc-42', 'kb-doc-128']} />);
    expect(screen.getByText('kb-doc-42')).toBeInTheDocument();
    expect(screen.getByText('kb-doc-128')).toBeInTheDocument();
  });

  it('shows similarity scores', () => {
    render(<VectorMap memoryHits={['kb-doc-42']} />);
    expect(screen.getByText(/sim: 0.95/)).toBeInTheDocument();
  });

  it('handles no hits', () => {
    render(<VectorMap memoryHits={[]} />);
    expect(screen.getByText(/0 hits/)).toBeInTheDocument();
  });
});
