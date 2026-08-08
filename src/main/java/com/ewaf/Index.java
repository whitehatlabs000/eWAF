package com.ewaf;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// el string vacío "" atrapa la ruta base (/)
@WebServlet({"", "/index", "/home"})
public class Index extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // Simplemente servimos el JSP que ahora está protegido dentro de WEB-INF
        req.getRequestDispatcher("/WEB-INF/jsp/index.jsp").forward(req, resp);

    }
}