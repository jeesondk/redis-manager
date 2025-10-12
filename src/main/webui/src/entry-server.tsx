
import App from './App'
import { renderToString } from 'react-dom/server'
import {StaticRouter} from "react-router-dom";

export function render(url: string) {
    const html = renderToString(
        <StaticRouter location={url}>
            <App />
        </StaticRouter>
    )
    return { html }
}
