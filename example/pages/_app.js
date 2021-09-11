import '../styles/globals.css'
import app from "../compiled/example.app"

app.init()

function MyApp({ Component, pageProps }) {
  return <Component {...pageProps} />
}

export default MyApp
