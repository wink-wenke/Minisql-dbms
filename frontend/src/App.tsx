import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Architecture from './pages/Architecture';
import Playground from './pages/Playground';
import Pipeline from './pages/Pipeline';
import Storage from './pages/Storage';
import Tests from './pages/Tests';
import FuzzDashboard from './pages/FuzzDashboard';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Architecture />} />
          <Route path="/playground" element={<Playground />} />
          <Route path="/pipeline" element={<Pipeline />} />
          <Route path="/storage" element={<Storage />} />
          <Route path="/tests" element={<Tests />} />
          <Route path="/fuzz" element={<FuzzDashboard />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
