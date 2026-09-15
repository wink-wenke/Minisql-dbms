import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import ErrorBoundary from './components/ErrorBoundary';
import Dashboard from './pages/Dashboard';
import Console from './pages/Console';
import Pipeline from './pages/Pipeline';
import Storage from './pages/Storage';
import Tests from './pages/Tests';
import History from './pages/History';
import Logs from './pages/Logs';
import NotFound from './pages/NotFound';

export default function App() {
  return (
    <BrowserRouter>
      <ErrorBoundary>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/console" element={<Console />} />
            <Route path="/pipeline" element={<Pipeline />} />
            <Route path="/storage" element={<Storage />} />
            <Route path="/tests" element={<Tests />} />
            <Route path="/history" element={<History />} />
            <Route path="/logs" element={<Logs />} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </ErrorBoundary>
    </BrowserRouter>
  );
}
