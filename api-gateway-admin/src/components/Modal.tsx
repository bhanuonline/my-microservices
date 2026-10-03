import { ReactNode } from 'react';

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
  actions?: ReactNode;
  wide?: boolean;
}

export function Modal({ title, onClose, children, actions, wide }: ModalProps) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className={wide ? 'modal wide' : 'modal'}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <header className="modal-header">
          <h2>{title}</h2>
          <button onClick={onClose} className="close" aria-label="close">×</button>
        </header>
        <div className="modal-body">{children}</div>
        {actions && <footer className="modal-footer">{actions}</footer>}
      </div>
    </div>
  );
}
